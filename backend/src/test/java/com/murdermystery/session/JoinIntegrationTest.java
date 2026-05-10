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
import org.springframework.web.client.RestTemplate;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JoinIntegrationTest {

    @LocalServerPort
    int port;

    private RestTemplate http;

    @BeforeEach
    void setUp() {
        http = new RestTemplate();
    }

    // SockJS endpoint base URL (SockJsClient handles transport negotiation)
    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    private WebSocketStompClient buildStompClient() {
        var transport = new WebSocketTransport(new StandardWebSocketClient());
        var client = new WebSocketStompClient(new SockJsClient(List.of(transport)));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private CreateSessionResponse createHostSession() {
        var response = http.postForEntity(
            baseUrl() + "/api/sessions",
            new CreateSessionRequest("toy-manor", "alice"),
            CreateSessionResponse.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    @Test
    void subscriberReceivesPlayerJoinedEvent_whenAnotherPlayerJoinsViaRest() throws Exception {
        CreateSessionResponse host = createHostSession();

        WebSocketStompClient stompClient = buildStompClient();
        // join now emits PLAYER_JOINED + LOBBY_COUNT_CHANGED = 2 frames
        CountDownLatch latch = new CountDownLatch(2);
        BlockingQueue<String> receivedFrames = new LinkedBlockingQueue<>();

        StompSession stompSession = stompClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(StompSession sess, Throwable ex) {
                    System.out.println("STOMP transport error: " + ex);
                }
            })
            .get(5, TimeUnit.SECONDS);

        System.out.println("STOMP connected, subscribing to /topic/session/" + host.sessionId() + "/event");
        stompSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    System.out.println("STOMP frame received: " + payload);
                    receivedFrames.offer(payload != null ? payload.toString() : "");
                    latch.countDown();
                }
            }
        );

        // Give time for subscription to be registered
        Thread.sleep(200);

        // Guest joins via REST
        var joinRes = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );
        assertThat(joinRes.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(latch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for PLAYER_JOINED + LOBBY_COUNT_CHANGED events")
            .isTrue();
        List<String> frames = new ArrayList<>(receivedFrames);
        assertThat(frames).anySatisfy(f -> assertThat(f)
            .contains("PLAYER_JOINED").contains("bob").contains(host.sessionId()));
        assertThat(frames).anySatisfy(f -> assertThat(f).contains("LOBBY_COUNT_CHANGED"));

        stompSession.disconnect();
        stompClient.stop();
    }

    @Test
    void subscriberReceivesLobbyCountChangedAfterPlayerJoined() throws Exception {
        CreateSessionResponse host = createHostSession();

        WebSocketStompClient stompClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(2);
        BlockingQueue<String> receivedFrames = new LinkedBlockingQueue<>();

        StompSession stompSession = stompClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        stompSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    receivedFrames.offer(payload != null ? payload.toString() : "");
                    latch.countDown();
                }
            }
        );
        Thread.sleep(200);

        http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        List<String> frames = new ArrayList<>(receivedFrames);
        String countFrame = frames.stream()
            .filter(f -> f.contains("LOBBY_COUNT_CHANGED"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No LOBBY_COUNT_CHANGED frame received"));
        assertThat(countFrame).contains("\"joined\":2").contains("\"required\":3");

        stompSession.disconnect();
        stompClient.stop();
    }

    @Test
    void getSession_returnsCurrentJoinedAndRequiredCount() {
        CreateSessionResponse host = createHostSession();

        http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            new JoinRequest("bob"),
            JoinResponse.class
        );

        var res = http.getForEntity(
            baseUrl() + "/api/sessions/" + host.sessionId(),
            SessionViewResponse.class
        );
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        SessionViewResponse view = res.getBody();
        assertThat(view).isNotNull();
        assertThat(view.joinedCount()).isEqualTo(2);
        assertThat(view.requiredCharacterCount()).isEqualTo(3);
        assertThat(view.inviteCode()).isEqualTo(host.inviteCode());
    }

    @Test
    void sameDeviceId_sequentialJoin_returnsIdempotentPlayerId() {
        CreateSessionResponse host = createHostSession();
        UUID deviceId = UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var req = new HttpEntity<>(new JoinRequest("bob"), headers);

        var res1 = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            req,
            JoinResponse.class
        );
        assertThat(res1.getStatusCode().is2xxSuccessful()).isTrue();
        String firstPlayerId = res1.getBody().playerId();

        var res2 = http.postForEntity(
            baseUrl() + "/api/sessions/" + host.inviteCode() + "/join",
            req,
            JoinResponse.class
        );
        assertThat(res2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res2.getBody().playerId()).isEqualTo(firstPlayerId);
    }

    @Test
    void failedJoin_doesNotBroadcastEvent() throws Exception {
        CreateSessionResponse host = createHostSession();

        WebSocketStompClient stompClient = buildStompClient();
        CountDownLatch latch = new CountDownLatch(1);

        StompSession stompSession = stompClient
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        stompSession.subscribe(
            "/topic/session/" + host.sessionId() + "/event",
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    latch.countDown();
                }
            }
        );

        Thread.sleep(200);

        // Join with invalid invite code — should fail
        try {
            http.postForEntity(
                baseUrl() + "/api/sessions/BADCOD/join",
                new JoinRequest("bob"),
                String.class
            );
        } catch (Exception ignored) {
            // 400 error expected
        }

        assertThat(latch.await(2, TimeUnit.SECONDS))
            .withFailMessage("Expected no PLAYER_JOINED event but latch was counted down")
            .isFalse();

        stompSession.disconnect();
        stompClient.stop();
    }

    @Test
    void concurrentJoin_sameDeviceId_bothSucceedWithSamePlayerId() throws Exception {
        // Validates the PG partial unique index (V4__player_device_unique.sql):
        //   CREATE UNIQUE INDEX ... ON players(session_id, device_id) WHERE device_id IS NOT NULL
        // When two threads race with the same deviceId, one hits the constraint and falls into
        // the outer-catch idempotent recovery path — both must return the same playerId.
        // This test cannot pass on H2 because H2 does not enforce partial unique indexes.
        CreateSessionResponse host = createHostSession();
        UUID deviceId = UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var req = new HttpEntity<>(new JoinRequest("concurrent"), headers);

        AtomicReference<String> playerId1 = new AtomicReference<>();
        AtomicReference<String> playerId2 = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            var res = http.postForEntity(baseUrl() + "/api/sessions/" + host.inviteCode() + "/join", req, JoinResponse.class);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            playerId1.set(res.getBody().playerId());
        });
        pool.submit(() -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            var res = http.postForEntity(baseUrl() + "/api/sessions/" + host.inviteCode() + "/join", req, JoinResponse.class);
            assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
            playerId2.set(res.getBody().playerId());
        });

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(playerId1.get()).isNotNull();
        assertThat(playerId1.get()).isEqualTo(playerId2.get());
    }
}
