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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PrivateTalkIntegrationTest {

    @LocalServerPort int port;

    @Autowired PrivateTalkService privateTalkService;
    @Autowired PrivateTalkRepository privateTalkRepository;
    @Autowired RoundTurnService roundTurnService;
    @Autowired RoundLifecycleService roundLifecycleService;

    private RestTemplate http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() { http = new RestTemplate(); }

    @AfterEach
    void tearDown() {
        privateTalkService.cancelPendingTimeoutsForTest();
        roundTurnService.cancelPendingAutoSelectsForTest();
        roundLifecycleService.cancelPendingDeadlinesForTest();
    }

    // ── STOMP helpers ──────────────────────────────────────────────────

    private String wsUrl() { return "http://localhost:" + port + "/ws"; }
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

    private void assertNoFrame(BlockingQueue<String> queue, String mustNotContain, long pollMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + pollMs;
        while (System.currentTimeMillis() < deadline) {
            String frame = queue.poll(50, TimeUnit.MILLISECONDS);
            if (frame != null && frame.contains(mustNotContain)) {
                throw new AssertionError("Unexpected frame containing '" + mustNotContain + "': " + frame);
            }
        }
    }

    // ── Game setup helpers ─────────────────────────────────────────────

    private record PlayerInfo(String sessionId, String inviteCode, String nickname,
                              UUID deviceId, String playerId) {}

    private PlayerInfo createHost() {
        UUID deviceId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = http.postForObject(
            baseUrl() + "/api/sessions",
            new HttpEntity<>(new CreateSessionRequest("toy-manor", "alice"), headers),
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
            new HttpEntity<>(null, headers),
            String.class
        );
    }

    private void postTutorialAck(String sessionId, UUID deviceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Device-Id", deviceId.toString());
        http.postForObject(
            baseUrl() + "/api/sessions/" + sessionId + "/tutorial-ack",
            new HttpEntity<>(null, headers),
            String.class
        );
    }

    // ── Round setup ────────────────────────────────────────────────────

    private record RoundSetup(
        PlayerInfo host, PlayerInfo guest1, PlayerInfo guest2,
        StompSession s1, StompSession s2, StompSession s3,
        WebSocketStompClient c1, WebSocketStompClient c2, WebSocketStompClient c3,
        BlockingQueue<String> topicFrames,
        BlockingQueue<String> privateFrames1,
        BlockingQueue<String> privateFrames2,
        BlockingQueue<String> privateFrames3
    ) {
        void disconnect() {
            try { s1.disconnect(); } catch (Exception ignored) {}
            try { s2.disconnect(); } catch (Exception ignored) {}
            try { s3.disconnect(); } catch (Exception ignored) {}
            c1.stop(); c2.stop(); c3.stop();
        }
    }

    private RoundSetup setupToRound() throws Exception {
        PlayerInfo host   = createHost();
        PlayerInfo guest1 = joinGuest(host.inviteCode(), "bob");
        PlayerInfo guest2 = joinGuest(host.inviteCode(), "charlie");

        WebSocketStompClient c1 = buildStompClient();
        WebSocketStompClient c2 = buildStompClient();
        WebSocketStompClient c3 = buildStompClient();

        BlockingQueue<String> topicFrames    = new LinkedBlockingQueue<>();
        BlockingQueue<String> privateFrames1 = new LinkedBlockingQueue<>();
        BlockingQueue<String> privateFrames2 = new LinkedBlockingQueue<>();
        BlockingQueue<String> privateFrames3 = new LinkedBlockingQueue<>();

        CountDownLatch cardLatch = new CountDownLatch(3);
        CountDownLatch turnLatch = new CountDownLatch(1);

        StompSession s1 = connectAuthenticated(c1, host.inviteCode(), "alice",   host.playerId());
        StompSession s2 = connectAuthenticated(c2, host.inviteCode(), "bob",     guest1.playerId());
        StompSession s3 = connectAuthenticated(c3, host.inviteCode(), "charlie", guest2.playerId());

        String topicDest = "/topic/session/" + host.sessionId() + "/event";
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
        s3.subscribe(topicDest, topicHandler);

        String privateDest = "/user/queue/session/" + host.sessionId() + "/private";
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
        s3.subscribe(privateDest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                String frame = payload != null ? payload.toString() : "";
                privateFrames3.offer(frame);
                if (frame.contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        });

        // Give broker time to register subscriptions before triggering game start events
        Thread.sleep(300);

        postStart(host);

        assertThat(cardLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for CHARACTER_CARD_DEALT × 3").isTrue();

        awaitFrame(topicFrames, "\"tutorial\"", 3000);

        postTutorialAck(host.sessionId(), host.deviceId());
        postTutorialAck(host.sessionId(), guest1.deviceId());
        postTutorialAck(host.sessionId(), guest2.deviceId());

        assertThat(turnLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for TURN_STARTED").isTrue();

        return new RoundSetup(host, guest1, guest2, s1, s2, s3, c1, c2, c3,
            topicFrames, privateFrames1, privateFrames2, privateFrames3);
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void request_accept_end_fullCycle() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            privateTalkService.request(sessId, aliceId, bobId);

            // REQUESTED delivered to both requester and target private queues
            awaitFrame(setup.privateFrames1(), "PRIVATE_TALK_REQUESTED", 3000);
            awaitFrame(setup.privateFrames2(), "PRIVATE_TALK_REQUESTED", 3000);

            PrivateTalk talk = privateTalkRepository.findActiveBySessionId(sessId)
                .orElseThrow(() -> new AssertionError("Expected active private_talk row"));

            privateTalkService.accept(sessId, talk.getId(), bobId);

            String startedFrame = awaitFrame(setup.topicFrames(), "PRIVATE_TALK_STARTED", 3000);
            assertThat(startedFrame).contains(aliceId.toString()).contains(bobId.toString());

            privateTalkService.end(sessId, talk.getId(), aliceId);

            String endedFrame = awaitFrame(setup.topicFrames(), "PRIVATE_TALK_ENDED", 3000);
            assertThat(endedFrame).contains("USER_ENDED");

            // DB: row fully closed
            PrivateTalk completed = privateTalkRepository.findById(talk.getId()).orElseThrow();
            assertThat(completed.getEndReason()).isEqualTo("USER_ENDED");
            assertThat(completed.getStartedAt()).isNotNull();
            assertThat(completed.getEndedAt()).isNotNull();

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void request_reject_noBroadcast() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            privateTalkService.request(sessId, aliceId, bobId);
            awaitFrame(setup.privateFrames2(), "PRIVATE_TALK_REQUESTED", 3000);

            PrivateTalk talk = privateTalkRepository.findActiveBySessionId(sessId).orElseThrow();

            privateTalkService.reject(sessId, talk.getId(), bobId);

            // DB assertion first to let the broker flush before negative frame check
            PrivateTalk rejected = privateTalkRepository.findById(talk.getId()).orElseThrow();
            assertThat(rejected.getEndReason()).isEqualTo("REJECTED");

            assertNoFrame(setup.topicFrames(), "PRIVATE_TALK_STARTED", 1500);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void request_timeout_noBroadcast() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            privateTalkService.request(sessId, aliceId, bobId);
            awaitFrame(setup.privateFrames1(), "PRIVATE_TALK_REQUESTED", 3000);

            PrivateTalk talk = privateTalkRepository.findActiveBySessionId(sessId).orElseThrow();

            // Simulate 60s timeout expiry by calling the package-private method directly
            privateTalkService.timeoutRequest(talk.getId());

            // DB assertion first to let the broker flush before negative frame checks
            PrivateTalk timedOut = privateTalkRepository.findById(talk.getId()).orElseThrow();
            assertThat(timedOut.getEndReason()).isEqualTo("TIMEOUT");

            assertNoFrame(setup.topicFrames(), "PRIVATE_TALK_STARTED", 1500);
            assertNoFrame(setup.topicFrames(), "PRIVATE_TALK_ENDED", 500);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void secondRequest_whileActive_isSilentlyIgnored() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId    = UUID.fromString(setup.host().sessionId());
            UUID aliceId   = UUID.fromString(setup.host().playerId());
            UUID bobId     = UUID.fromString(setup.guest1().playerId());
            UUID charlieId = UUID.fromString(setup.guest2().playerId());

            privateTalkService.request(sessId, aliceId, bobId);
            awaitFrame(setup.privateFrames1(), "PRIVATE_TALK_REQUESTED", 3000);

            // Second request while the first is still active is swallowed silently
            privateTalkService.request(sessId, charlieId, aliceId);

            // Exactly one private_talk row for this session
            long count = privateTalkRepository.findAll().stream()
                .filter(t -> t.getSessionId().equals(sessId))
                .count();
            assertThat(count).isEqualTo(1);
            assertThat(privateTalkRepository.findActiveBySessionId(sessId)).isPresent();

            // Charlie's second request was silently swallowed — no REQUESTED frame to charlie or alice
            assertNoFrame(setup.privateFrames3(), "PRIVATE_TALK_REQUESTED", 500);
            assertNoFrame(setup.privateFrames1(), "PRIVATE_TALK_REQUESTED", 200);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void activeTalk_endByRoundBoundary_broadcastsEnded() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            privateTalkService.request(sessId, aliceId, bobId);
            PrivateTalk talk = privateTalkRepository.findActiveBySessionId(sessId).orElseThrow();

            privateTalkService.accept(sessId, talk.getId(), bobId);
            awaitFrame(setup.topicFrames(), "PRIVATE_TALK_STARTED", 3000);

            privateTalkService.endByRoundBoundary(sessId, 1);

            String endedFrame = awaitFrame(setup.topicFrames(), "PRIVATE_TALK_ENDED", 3000);
            assertThat(endedFrame).contains("ROUND_BOUNDARY");

            PrivateTalk closed = privateTalkRepository.findById(talk.getId()).orElseThrow();
            assertThat(closed.getEndReason()).isEqualTo("ROUND_BOUNDARY");

        } finally {
            setup.disconnect();
        }
    }
}
