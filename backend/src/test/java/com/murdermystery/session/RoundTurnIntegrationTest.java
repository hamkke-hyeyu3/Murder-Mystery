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
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RoundTurnIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    RoundTurnService roundTurnService;

    @Autowired
    LocationOccupancyRepository occupancyRepository;

    @Autowired
    ClueAclRepository clueAclRepository;

    private RestTemplate http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        http = new RestTemplate();
    }

    @AfterEach
    void tearDown() {
        roundTurnService.cancelPendingAutoSelectsForTest();
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

    private StompFrameHandler queueHandler(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                queue.offer(payload != null ? payload.toString() : "");
            }
        };
    }

    /** Drains queue until a frame containing the substring is found. */
    private String awaitFrame(BlockingQueue<String> queue, String containing, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String frame = queue.poll(100, TimeUnit.MILLISECONDS);
            if (frame != null && frame.contains(containing)) return frame;
        }
        throw new AssertionError("Timed out waiting for frame containing: " + containing);
    }

    /** Collects exactly `count` frames that contain the substring, within timeoutMs. */
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
        BlockingQueue<String> privateFrames3,
        String firstTurnPlayerId,
        int firstTurnIndex,
        List<String> firstTurnCandidates
    ) {
        StompSession sessionFor(String playerId) {
            if (playerId.equals(host.playerId())) return s1;
            if (playerId.equals(guest1.playerId())) return s2;
            return s3;
        }

        BlockingQueue<String> privateQueueFor(String playerId) {
            if (playerId.equals(host.playerId())) return privateFrames1;
            if (playerId.equals(guest1.playerId())) return privateFrames2;
            return privateFrames3;
        }

        void disconnect() {
            try { s1.disconnect(); } catch (Exception ignored) {}
            try { s2.disconnect(); } catch (Exception ignored) {}
            try { s3.disconnect(); } catch (Exception ignored) {}
            c1.stop(); c2.stop(); c3.stop();
        }
    }

    /**
     * Brings the game to the TURN_STARTED state for round 1, turn 0.
     * All 3 STOMP sessions are connected and subscribed to topic + private.
     */
    private RoundSetup setupToRound() throws Exception {
        PlayerInfo host = createHost();
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

        StompSession s1 = connectAuthenticated(c1, host.inviteCode(), "alice", host.playerId());
        StompSession s2 = connectAuthenticated(c2, host.inviteCode(), "bob",   guest1.playerId());
        StompSession s3 = connectAuthenticated(c3, host.inviteCode(), "charlie", guest2.playerId());

        // Topic subscription (all 3 clients share the same queue for simplicity)
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

        // Private subscriptions
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

        Thread.sleep(300); // let subscriptions settle

        postStart(host);

        assertThat(cardLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for CHARACTER_CARD_DEALT × 3").isTrue();

        // Wait for SESSION_STATE_CHANGED(tutorial) before acking — character_assignment → tutorial delay
        awaitFrame(topicFrames, "\"tutorial\"", 3000);

        postTutorialAck(host.sessionId(), host.deviceId());
        postTutorialAck(host.sessionId(), guest1.deviceId());
        postTutorialAck(host.sessionId(), guest2.deviceId());

        assertThat(turnLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for TURN_STARTED").isTrue();

        // Parse first TURN_STARTED frame
        String turnFrame = topicFrames.stream()
            .filter(f -> f.contains("TURN_STARTED"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No TURN_STARTED in topicFrames"));
        JsonNode payload = mapper.readTree(turnFrame).path("payload");
        String firstTurnPlayerId = payload.path("playerId").asText();
        int firstTurnIndex = payload.path("turnIndex").asInt();
        List<String> candidates = mapper.readerForListOf(String.class)
            .readValue(payload.path("candidateLocationIds"));

        return new RoundSetup(host, guest1, guest2, s1, s2, s3, c1, c2, c3,
            topicFrames, privateFrames1, privateFrames2, privateFrames3,
            firstTurnPlayerId, firstTurnIndex, candidates);
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void selectLocation_broadcastsToAllAndDeliversClueOnlyToSelector() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            String locationId = setup.firstTurnCandidates().get(0);
            String sessionId  = setup.host().sessionId();
            UUID   sessId     = UUID.fromString(sessionId);
            StompSession selectorSession = setup.sessionFor(setup.firstTurnPlayerId());

            // Send select-location via STOMP
            Map<String, Object> body = new HashMap<>();
            body.put("locationId", locationId);
            body.put("roundNumber", 1);
            body.put("turnIndex", setup.firstTurnIndex());
            selectorSession.send("/app/session/" + sessionId + "/select-location", body);

            // 3 subscribers → 3 LOCATION_SELECTED frames in topicFrames
            // Use exact match to exclude LOCATION_AUTO_SELECTED
            awaitFrames(setup.topicFrames(), "\"LOCATION_SELECTED\"", 3, 5000);

            // CLUE_DELIVERED arrives only in selector's private queue
            awaitFrame(setup.privateQueueFor(setup.firstTurnPlayerId()), "CLUE_DELIVERED", 5000);

            // DB: occupancy row
            var occupancies = occupancyRepository.findBySessionIdAndRoundNumber(sessId, 1);
            assertThat(occupancies).hasSize(1);
            assertThat(occupancies.get(0).getLocationId()).isEqualTo(locationId);
            assertThat(occupancies.get(0).isAutoSelected()).isFalse();

            // DB: ACL row for selector only
            UUID selectorId = UUID.fromString(setup.firstTurnPlayerId());
            var acls = clueAclRepository.findBySessionIdAndPlayerId(sessId, selectorId);
            assertThat(acls).isNotEmpty();
            assertThat(acls).allMatch(acl -> acl.getPlayerId().equals(selectorId));

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void autoSelect_callDirectly_broadcastsAutoSelectedAndDeliversClue() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            String sessionId = setup.host().sessionId();
            UUID   sessId    = UUID.fromString(sessionId);
            int playerCount  = 3; // toy-manor has 3 characters

            // Bypass scheduler — call autoSelectTurn directly (package-private, same package)
            roundTurnService.autoSelectTurn(sessId, 1, setup.firstTurnIndex(), playerCount);

            // 3 subscribers → 3 LOCATION_AUTO_SELECTED frames
            awaitFrames(setup.topicFrames(), "LOCATION_AUTO_SELECTED", 3, 5000);

            // CLUE_DELIVERED → selector's private queue
            awaitFrame(setup.privateQueueFor(setup.firstTurnPlayerId()), "CLUE_DELIVERED", 5000);

            // DB: occupancy row with autoSelected=true
            var occupancies = occupancyRepository.findBySessionIdAndRoundNumber(sessId, 1);
            assertThat(occupancies).hasSize(1);
            assertThat(occupancies.get(0).isAutoSelected()).isTrue();

            // DB: ACL for selector
            UUID selectorId = UUID.fromString(setup.firstTurnPlayerId());
            assertThat(clueAclRepository.findBySessionIdAndPlayerId(sessId, selectorId)).isNotEmpty();

            // No plain LOCATION_SELECTED should have been sent
            long plainSelectedCount = setup.topicFrames().stream()
                .filter(f -> f.contains("\"LOCATION_SELECTED\"")).count();
            assertThat(plainSelectedCount).isZero();

        } finally {
            setup.disconnect();
        }
    }
}
