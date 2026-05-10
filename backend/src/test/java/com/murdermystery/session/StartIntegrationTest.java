package com.murdermystery.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.murdermystery.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StartIntegrationTest {

    @LocalServerPort
    int port;

    private RestTemplate http;

    @BeforeEach
    void setUp() {
        http = new RestTemplate();
    }

    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private WebSocketStompClient buildStompClient() {
        var transport = new WebSocketTransport(new StandardWebSocketClient());
        var client = new WebSocketStompClient(new SockJsClient(List.of(transport)));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connectAuthenticated(WebSocketStompClient stompClient,
                                              String inviteCode, String nickname, String playerId)
            throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("X-Invite-Code", inviteCode);
        connectHeaders.add("X-Nickname", nickname);
        connectHeaders.add("X-Player-Id", playerId);
        return stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    private record PlayerInfo(String sessionId, String inviteCode, String nickname,
                              UUID deviceId, String playerId) {}

    private PlayerInfo createHost() {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var res = http.postForEntity(
            baseUrl() + "/api/sessions",
            new HttpEntity<>(new CreateSessionRequest("toy-manor", "alice"), headers),
            CreateSessionResponse.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        var body = res.getBody();
        return new PlayerInfo(body.sessionId(), body.inviteCode(), "alice", deviceId, body.playerId());
    }

    private PlayerInfo joinGuest(String inviteCode, String nickname) {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var res = http.postForEntity(
            baseUrl() + "/api/sessions/" + inviteCode + "/join",
            new HttpEntity<>(new JoinRequest(nickname), headers),
            JoinResponse.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        return new PlayerInfo(null, inviteCode, nickname, deviceId, res.getBody().playerId());
    }

    @Test
    void start_allClientsReceiveSessionStateChangedAndOwnCard() throws Exception {
        PlayerInfo host = createHost();
        PlayerInfo guest1 = joinGuest(host.inviteCode(), "bob");
        PlayerInfo guest2 = joinGuest(host.inviteCode(), "charlie");

        WebSocketStompClient c1 = buildStompClient();
        WebSocketStompClient c2 = buildStompClient();
        WebSocketStompClient c3 = buildStompClient();

        // 2 SESSION_STATE_CHANGED (intro + character_assignment) per client = 6 topic frames total
        CountDownLatch topicLatch = new CountDownLatch(6);
        BlockingQueue<String> topicFrames = new LinkedBlockingQueue<>();

        // 1 CHARACTER_CARD_DEALT per client = 3 private frames total
        CountDownLatch privateLatch = new CountDownLatch(3);
        List<String> privateFrames1 = new ArrayList<>();
        List<String> privateFrames2 = new ArrayList<>();
        List<String> privateFrames3 = new ArrayList<>();

        StompSession s1 = connectAuthenticated(c1, host.inviteCode(), "alice", host.playerId());
        StompSession s2 = connectAuthenticated(c2, host.inviteCode(), "bob", guest1.playerId());
        StompSession s3 = connectAuthenticated(c3, host.inviteCode(), "charlie", guest2.playerId());

        String topicDest = "/topic/session/" + host.sessionId() + "/event";
        StompFrameHandler topicHandler = new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                topicFrames.offer(payload != null ? payload.toString() : "");
                topicLatch.countDown();
            }
        };
        s1.subscribe(topicDest, topicHandler);
        s2.subscribe(topicDest, topicHandler);
        s3.subscribe(topicDest, topicHandler);

        String privateDest = "/user/queue/session/" + host.sessionId() + "/private";
        s1.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                synchronized (privateFrames1) { privateFrames1.add(payload != null ? payload.toString() : ""); }
                privateLatch.countDown();
            }
        });
        s2.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                synchronized (privateFrames2) { privateFrames2.add(payload != null ? payload.toString() : ""); }
                privateLatch.countDown();
            }
        });
        s3.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                synchronized (privateFrames3) { privateFrames3.add(payload != null ? payload.toString() : ""); }
                privateLatch.countDown();
            }
        });

        Thread.sleep(300);

        // Host triggers game start
        HttpHeaders startHeaders = new HttpHeaders();
        startHeaders.set("X-Device-Id", host.deviceId().toString());
        startHeaders.setContentType(MediaType.APPLICATION_JSON);
        var startRes = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.sessionId() + "/start",
            new HttpEntity<>(null, startHeaders),
            StartGameResponse.class
        );
        assertThat(startRes.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(startRes.getBody().phase()).isEqualTo("in_progress");
        assertThat(startRes.getBody().state()).isEqualTo("intro");
        assertThat(startRes.getBody().turnOrder()).hasSize(3);

        // All 6 topic frames (2 per client) arrive within 2 seconds
        assertThat(topicLatch.await(2, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for SESSION_STATE_CHANGED frames (got %d)", 6 - topicLatch.getCount())
            .isTrue();

        List<String> allTopicFrames = new ArrayList<>(topicFrames);
        long introCount = allTopicFrames.stream()
            .filter(f -> f.contains("SESSION_STATE_CHANGED") && f.contains("\"intro\"")).count();
        long assignmentCount = allTopicFrames.stream()
            .filter(f -> f.contains("SESSION_STATE_CHANGED") && f.contains("\"character_assignment\"")).count();
        assertThat(introCount).isEqualTo(3);       // one per subscriber
        assertThat(assignmentCount).isEqualTo(3);  // one per subscriber

        // All 3 private cards arrive within 2 seconds
        assertThat(privateLatch.await(2, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for CHARACTER_CARD_DEALT private frames")
            .isTrue();

        // Each client received exactly 1 card
        assertThat(privateFrames1).hasSize(1);
        assertThat(privateFrames2).hasSize(1);
        assertThat(privateFrames3).hasSize(1);

        // Cards contain CHARACTER_CARD_DEALT
        assertThat(privateFrames1.get(0)).contains("CHARACTER_CARD_DEALT");
        assertThat(privateFrames2.get(0)).contains("CHARACTER_CARD_DEALT");
        assertThat(privateFrames3.get(0)).contains("CHARACTER_CARD_DEALT");

        // No two clients got the same characterId (ACL: own card only)
        String id1 = extractCharacterId(privateFrames1.get(0));
        String id2 = extractCharacterId(privateFrames2.get(0));
        String id3 = extractCharacterId(privateFrames3.get(0));
        assertThat(List.of(id1, id2, id3)).doesNotHaveDuplicates();

        s1.disconnect(); s2.disconnect(); s3.disconnect();
        c1.stop(); c2.stop(); c3.stop();
    }

    @Test
    void start_byNonHostDevice_returns403() {
        PlayerInfo host = createHost();
        PlayerInfo guest1 = joinGuest(host.inviteCode(), "bob");
        joinGuest(host.inviteCode(), "charlie");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", guest1.deviceId().toString()); // non-host
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() -> http.postForEntity(
            baseUrl() + "/api/sessions/" + host.sessionId() + "/start",
            new HttpEntity<>(null, headers),
            String.class
        )).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    void start_withInsufficientPlayers_returns409() {
        PlayerInfo host = createHost();
        // Only 1 player joined (host only), toy-manor needs 3
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", host.deviceId().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() -> http.postForEntity(
            baseUrl() + "/api/sessions/" + host.sessionId() + "/start",
            new HttpEntity<>(null, headers),
            String.class
        )).isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    private String extractCharacterId(String frame) {
        // frame is a JSON string like {"type":"SESSION_EVENT","payload":{"characterId":"char-x",...}}
        int idx = frame.indexOf("\"characterId\":\"");
        if (idx < 0) return "";
        int start = idx + "\"characterId\":\"".length();
        int end = frame.indexOf("\"", start);
        return frame.substring(start, end);
    }
}
