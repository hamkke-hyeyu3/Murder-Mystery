package com.murdermystery.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.murdermystery.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "dev"})
@Import(TestcontainersConfiguration.class)
class RoundLifecycleIntegrationTest {

    @LocalServerPort int port;
    @Autowired RoundTurnService roundTurnService;
    @Autowired RoundLifecycleService roundLifecycleService;
    @Autowired ClueAclRepository clueAclRepository;
    @Autowired SessionRepository sessionRepository;

    private RestTemplate http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() { http = new RestTemplate(); }

    @AfterEach
    void tearDown() {
        roundTurnService.cancelPendingAutoSelectsForTest();
        roundLifecycleService.cancelPendingDeadlinesForTest();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private String wsUrl()   { return "http://localhost:" + port + "/ws"; }
    private String baseUrl() { return "http://localhost:" + port; }

    private WebSocketStompClient buildStompClient() {
        var transport = new WebSocketTransport(new StandardWebSocketClient());
        var client = new WebSocketStompClient(new SockJsClient(List.of(transport)));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connectAuthenticated(WebSocketStompClient stompClient,
                                              String inviteCode, String nickname, String playerId)
            throws Exception {
        StompHeaders h = new StompHeaders();
        h.add("X-Invite-Code", inviteCode);
        h.add("X-Nickname", nickname);
        h.add("X-Player-Id", playerId);
        return stompClient.connectAsync(wsUrl(), (WebSocketHttpHeaders) null, h,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    private String awaitFrame(BlockingQueue<String> queue, String containing, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String frame = queue.poll(100, TimeUnit.MILLISECONDS);
            if (frame != null && frame.contains(containing)) return frame;
        }
        throw new AssertionError("Timed out waiting for frame containing: " + containing);
    }

    private List<String> awaitFrames(BlockingQueue<String> queue, String containing,
                                     int count, long timeoutMs) throws InterruptedException {
        List<String> found = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (found.size() < count && System.currentTimeMillis() < deadline) {
            String frame = queue.poll(100, TimeUnit.MILLISECONDS);
            if (frame != null && frame.contains(containing)) found.add(frame);
        }
        if (found.size() < count) {
            throw new AssertionError("Expected " + count + " frames containing '" + containing
                + "' but got " + found.size());
        }
        return found;
    }

    // ── Game setup ──────────────────────────────────────────────────────

    private record PlayerInfo(String sessionId, String inviteCode, String nickname,
                              UUID deviceId, String playerId) {}

    private PlayerInfo createHostWithDevDuo() {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = http.postForObject(
            baseUrl() + "/api/sessions",
            new HttpEntity<>(new CreateSessionRequest("dev-duo", "alice"), headers),
            CreateSessionResponse.class
        );
        return new PlayerInfo(body.sessionId(), body.inviteCode(), "alice", deviceId, body.playerId());
    }

    private PlayerInfo joinGuest(String inviteCode, String nickname) {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = http.postForObject(
            baseUrl() + "/api/sessions/" + inviteCode + "/join",
            new HttpEntity<>(new JoinRequest(nickname), headers),
            JoinResponse.class
        );
        return new PlayerInfo(null, inviteCode, nickname, deviceId, body.playerId());
    }

    private void postStart(PlayerInfo host) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", host.deviceId().toString());
        http.postForObject(
            baseUrl() + "/api/sessions/" + host.sessionId() + "/start",
            new HttpEntity<>(null, headers), String.class);
    }

    private void postTutorialAck(String sessionId, UUID deviceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        http.postForObject(
            baseUrl() + "/api/sessions/" + sessionId + "/tutorial-ack",
            new HttpEntity<>(null, headers), String.class);
    }

    private record DuoSetup(
        PlayerInfo host, PlayerInfo guest,
        StompSession s1, StompSession s2,
        WebSocketStompClient c1, WebSocketStompClient c2,
        BlockingQueue<String> topicFrames,
        BlockingQueue<String> privateFrames1,
        BlockingQueue<String> privateFrames2
    ) {
        void disconnect() {
            try { s1.disconnect(); } catch (Exception ignored) {}
            try { s2.disconnect(); } catch (Exception ignored) {}
            c1.stop(); c2.stop();
        }
    }

    /**
     * Drives a 2-player dev-duo game to TURN_STARTED state (round 1, turn 0).
     */
    private DuoSetup setupToRoundDuo() throws Exception {
        PlayerInfo host  = createHostWithDevDuo();
        PlayerInfo guest = joinGuest(host.inviteCode(), "bob");

        WebSocketStompClient c1 = buildStompClient();
        WebSocketStompClient c2 = buildStompClient();

        BlockingQueue<String> topicFrames    = new LinkedBlockingQueue<>();
        BlockingQueue<String> privateFrames1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> privateFrames2 = new LinkedBlockingQueue<>();

        CountDownLatch cardLatch = new CountDownLatch(2);
        CountDownLatch turnLatch = new CountDownLatch(1);

        StompSession s1 = connectAuthenticated(c1, host.inviteCode(), "alice", host.playerId());
        StompSession s2 = connectAuthenticated(c2, host.inviteCode(), "bob",   guest.playerId());

        String topicDest   = "/topic/session/" + host.sessionId() + "/event";
        String privateDest = "/user/queue/session/" + host.sessionId() + "/private";

        StompFrameHandler topicHandler = new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                String frame = payload != null ? payload.toString() : "";
                topicFrames.offer(frame);
                if (frame.contains("TURN_STARTED")) turnLatch.countDown();
            }
        };
        s1.subscribe(topicDest, topicHandler);
        s2.subscribe(topicDest, topicHandler);

        s1.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                String frame = payload != null ? payload.toString() : "";
                privateFrames1.offer(frame);
                if (frame.contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        });
        s2.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                String frame = payload != null ? payload.toString() : "";
                privateFrames2.offer(frame);
                if (frame.contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        });

        Thread.sleep(300);

        postStart(host);

        assertThat(cardLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for CHARACTER_CARD_DEALT × 2").isTrue();

        awaitFrame(topicFrames, "\"tutorial\"", 3000);

        postTutorialAck(host.sessionId(), host.deviceId());
        postTutorialAck(host.sessionId(), guest.deviceId());

        assertThat(turnLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for TURN_STARTED").isTrue();

        return new DuoSetup(host, guest, s1, s2, c1, c2,
            topicFrames, privateFrames1, privateFrames2);
    }

    // ── Tests ───────────────────────────────────────────────────────────

    /**
     * Clues accumulated in round 1 are still present after round 2 completes.
     * Verifies SPEC §7 "단서는 monotonic 누적 — 이전 라운드 단서 삭제 금지".
     */
    @Test
    void cluesPersistAcrossRounds() throws Exception {
        DuoSetup setup = setupToRoundDuo();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            // ── Round 1: drive all 2 turns with autoSelect ──────────────
            roundTurnService.autoSelectTurn(sessId, 1, 0, 2);
            roundTurnService.autoSelectTurn(sessId, 1, 1, 2);

            // Wait for round 2 to start automatically after round 1 ends
            awaitFrame(setup.topicFrames(), "ROUND_STARTED", 5000);
            // also wait for the TURN_STARTED of round 2 to land
            awaitFrame(setup.topicFrames(), "TURN_STARTED", 5000);

            // ── Round 2: drive all 2 turns ───────────────────────────────
            roundTurnService.autoSelectTurn(sessId, 2, 0, 2);
            roundTurnService.autoSelectTurn(sessId, 2, 1, 2);

            // Wait for vote transition
            awaitFrame(setup.topicFrames(), "SESSION_STATE_CHANGED", 5000);

            // Each player got exactly 2 clues (one per round) — 4 ACL rows total for this session
            List<ClueAcl> hostAcls  = clueAclRepository.findBySessionIdAndPlayerId(sessId, hostId);
            List<ClueAcl> guestAcls = clueAclRepository.findBySessionIdAndPlayerId(sessId, guestId);
            assertThat(hostAcls).hasSize(2);
            assertThat(guestAcls).hasSize(2);

        } finally {
            setup.disconnect();
        }
    }

    /**
     * After the final round completes, the session transitions to phase='vote'
     * and SESSION_STATE_CHANGED("vote") is broadcast to all clients.
     */
    @Test
    void lastRoundTransitionsToVote() throws Exception {
        DuoSetup setup = setupToRoundDuo();
        try {
            UUID sessId = UUID.fromString(setup.host().sessionId());

            // ── Round 1 ──────────────────────────────────────────────────
            roundTurnService.autoSelectTurn(sessId, 1, 0, 2);
            roundTurnService.autoSelectTurn(sessId, 1, 1, 2);
            awaitFrame(setup.topicFrames(), "ROUND_STARTED", 5000);

            // ── Round 2 ──────────────────────────────────────────────────
            // Wait for TURN_STARTED so session state is updated before autoSelect
            awaitFrame(setup.topicFrames(), "TURN_STARTED", 5000);
            roundTurnService.autoSelectTurn(sessId, 2, 0, 2);
            roundTurnService.autoSelectTurn(sessId, 2, 1, 2);

            // Both subscribers should receive SESSION_STATE_CHANGED("vote")
            List<String> voteMsgs = awaitFrames(setup.topicFrames(), "\"vote\"", 2, 5000);
            assertThat(voteMsgs).allMatch(msg -> msg.contains("SESSION_STATE_CHANGED"));

            // DB: session.state must be 'vote'
            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getState()).isEqualTo("vote");

            // No ROUND_STARTED(3) should have been emitted (dev-duo has only 2 rounds)
            long round3StartedCount = setup.topicFrames().stream()
                .filter(f -> f.contains("ROUND_STARTED"))
                .filter(f -> {
                    try {
                        return mapper.readTree(f).path("payload").path("roundNumber").asInt() == 3;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();
            assertThat(round3StartedCount).isZero();

        } finally {
            setup.disconnect();
        }
    }
}
