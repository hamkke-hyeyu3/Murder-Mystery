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
import java.time.Instant;
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
class ItemIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    RoundTurnService roundTurnService;

    @Autowired
    ClueRepository clueRepository;

    @Autowired
    ClueAclRepository clueAclRepository;

    @Autowired
    ItemActionRepository itemActionRepository;

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

    /** Asserts no frame containing the substring arrives within pollMs. */
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

    // ── Clue seeding helper ────────────────────────────────────────────

    private Clue seedClue(UUID sessionId, String itemId, String locationId, UUID ownerId) {
        Instant now = Instant.now();
        Clue clue = new Clue(sessionId, 1, itemId, locationId, itemId, ownerId, now);
        clueRepository.save(clue);
        clueAclRepository.save(new ClueAcl(clue.getId(), ownerId, now, "discovery"));
        return clue;
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

        // STOMP subscribe() is fire-and-forget — no server ACK. Sleep gives the broker
        // time to register all 6 subscriptions before we trigger game start events.
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
    void exchange_swapsOwnersAndAddsAclsAndBroadcasts() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            Clue clueX = seedClue(sessId, "torn-letter",  "library", aliceId);
            Clue clueY = seedClue(sessId, "poison-vial",  "kitchen", bobId);

            Map<String, Object> body = new HashMap<>();
            body.put("partnerPlayerId",  setup.guest1().playerId());
            body.put("requesterClueId", clueX.getId().toString());
            body.put("partnerClueId",   clueY.getId().toString());
            setup.s1().send("/app/session/" + setup.host().sessionId() + "/item-exchange", body);

            awaitFrames(setup.topicFrames(), "\"ITEM_EXCHANGED\"", 3, 5000);

            // Alice (s1) 는 이제 Y 소유 → CLUE_DELIVERED
            String aliceDelivery = awaitFrame(setup.privateFrames1(), "CLUE_DELIVERED", 3000);
            assertThat(aliceDelivery).contains("\"source\":\"exchange\"");
            // Bob (s2) 는 이제 X 소유 → CLUE_DELIVERED
            String bobDelivery = awaitFrame(setup.privateFrames2(), "CLUE_DELIVERED", 3000);
            assertThat(bobDelivery).contains("\"source\":\"exchange\"");
            // Charlie (s3) 는 수신 없음
            assertNoFrame(setup.privateFrames3(), "CLUE_DELIVERED", 200);

            // DB: 소유권 교환
            assertThat(clueRepository.findById(clueX.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(bobId);
            assertThat(clueRepository.findById(clueY.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(aliceId);

            // DB: 신규 ACL (X→Bob, Y→Alice), source="exchange"
            assertThat(clueAclRepository.findById(new ClueAclId(clueX.getId(), bobId)))
                .isPresent().get().extracting(ClueAcl::getSource).isEqualTo("exchange");
            assertThat(clueAclRepository.findById(new ClueAclId(clueY.getId(), aliceId)))
                .isPresent().get().extracting(ClueAcl::getSource).isEqualTo("exchange");

            // DB: item_actions 1건 (이 세션 한정)
            var actions = itemActionRepository.findAll().stream()
                .filter(a -> a.getSessionId().equals(sessId)).toList();
            assertThat(actions).hasSize(1);
            ItemAction action = actions.get(0);
            assertThat(action.getActionType()).isEqualTo("exchange");
            assertThat(action.getActorPlayerId()).isEqualTo(aliceId);
            assertThat(action.getTargetPlayerId()).isEqualTo(bobId);
            assertThat(action.getActorClueId()).isEqualTo(clueX.getId());
            assertThat(action.getTargetClueId()).isEqualTo(clueY.getId());
            assertThat(action.getRecipientPlayerIds()).isNull();

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void shareFull_grantsAclToAllOthersAndBroadcasts() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId    = UUID.fromString(setup.host().sessionId());
            UUID aliceId   = UUID.fromString(setup.host().playerId());
            UUID bobId     = UUID.fromString(setup.guest1().playerId());
            UUID charlieId = UUID.fromString(setup.guest2().playerId());

            Clue clueX = seedClue(sessId, "torn-letter", "library", aliceId);

            Map<String, Object> body = new HashMap<>();
            body.put("clueId", clueX.getId().toString());
            setup.s1().send("/app/session/" + setup.host().sessionId() + "/item-share-full", body);

            awaitFrames(setup.topicFrames(), "\"ITEM_SHARED_FULL\"", 3, 5000);

            // Bob, Charlie 각 CLUE_DELIVERED (source=share_all)
            String bobDelivery     = awaitFrame(setup.privateFrames2(), "CLUE_DELIVERED", 3000);
            String charlieDelivery = awaitFrame(setup.privateFrames3(), "CLUE_DELIVERED", 3000);
            assertThat(bobDelivery).contains("\"source\":\"share_all\"");
            assertThat(charlieDelivery).contains("\"source\":\"share_all\"");
            // Alice (actor) 는 수신 없음 — ItemService.java:179
            assertNoFrame(setup.privateFrames1(), "CLUE_DELIVERED", 200);

            // DB: ACL Bob, Charlie — source="share_all"
            assertThat(clueAclRepository.findById(new ClueAclId(clueX.getId(), bobId)))
                .isPresent().get().extracting(ClueAcl::getSource).isEqualTo("share_all");
            assertThat(clueAclRepository.findById(new ClueAclId(clueX.getId(), charlieId)))
                .isPresent().get().extracting(ClueAcl::getSource).isEqualTo("share_all");

            // DB: 소유권 불변
            assertThat(clueRepository.findById(clueX.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(aliceId);

            // DB: item_actions 1건 (이 세션 한정)
            var actions = itemActionRepository.findAll().stream()
                .filter(a -> a.getSessionId().equals(sessId)).toList();
            assertThat(actions).hasSize(1);
            ItemAction action = actions.get(0);
            assertThat(action.getActionType()).isEqualTo("share_all");
            assertThat(action.getActorPlayerId()).isEqualTo(aliceId);
            assertThat(action.getActorClueId()).isEqualTo(clueX.getId());
            assertThat(action.getTargetPlayerId()).isNull();
            assertThat(action.getTargetClueId()).isNull();
            assertThat(action.getRecipientPlayerIds()).isNull();

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void exchange_actorDoesNotOwnRequesterClue_isNoOp() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID aliceId = UUID.fromString(setup.host().playerId());
            UUID bobId   = UUID.fromString(setup.guest1().playerId());

            // Alice owns X, Bob owns Y
            Clue clueX = seedClue(sessId, "torn-letter", "library", aliceId);
            Clue clueY = seedClue(sessId, "poison-vial", "kitchen", bobId);

            // Bob sends exchange claiming Alice's clue X as the requesterClue — Bob doesn't own X
            Map<String, Object> body = new HashMap<>();
            body.put("partnerPlayerId",  setup.host().playerId());
            body.put("requesterClueId", clueX.getId().toString());
            body.put("partnerClueId",   clueY.getId().toString());
            setup.s2().send("/app/session/" + setup.host().sessionId() + "/item-exchange", body);

            // Anchor: Alice issues a legitimate share-full on a separate clue.
            // share-full and exchange broadcast on the same /topic/session/{id}/event.
            // When the 3rd ITEM_SHARED_FULL frame arrives, any concurrent ITEM_EXCHANGED
            // would have arrived on the same queue too — drains slow-CI flakiness.
            Clue anchorClue = seedClue(sessId, "candlestick", "lounge", aliceId);
            Map<String, Object> anchorBody = Map.of("clueId", anchorClue.getId().toString());
            setup.s1().send("/app/session/" + setup.host().sessionId() + "/item-share-full", anchorBody);

            int sharedFullCount = 0;
            long deadline = System.currentTimeMillis() + 5000;
            while (sharedFullCount < 3 && System.currentTimeMillis() < deadline) {
                String frame = setup.topicFrames().poll(100, TimeUnit.MILLISECONDS);
                if (frame == null) continue;
                if (frame.contains("ITEM_EXCHANGED")) {
                    throw new AssertionError("Bogus exchange was broadcast: " + frame);
                }
                if (frame.contains("ITEM_SHARED_FULL")) sharedFullCount++;
            }
            assertThat(sharedFullCount).isEqualTo(3);

            var actions = itemActionRepository.findAll().stream()
                .filter(a -> a.getSessionId().equals(sessId)).toList();
            assertThat(actions).noneMatch(a -> "exchange".equals(a.getActionType()));

            assertThat(clueRepository.findById(clueX.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(aliceId);
            assertThat(clueRepository.findById(clueY.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(bobId);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void sharePartial_grantsAclOnlyToRecipientsAndBroadcasts() throws Exception {
        RoundSetup setup = setupToRound();
        try {
            UUID sessId    = UUID.fromString(setup.host().sessionId());
            UUID aliceId   = UUID.fromString(setup.host().playerId());
            UUID bobId     = UUID.fromString(setup.guest1().playerId());
            UUID charlieId = UUID.fromString(setup.guest2().playerId());

            Clue clueX = seedClue(sessId, "torn-letter", "library", aliceId);

            Map<String, Object> body = new HashMap<>();
            body.put("clueId", clueX.getId().toString());
            body.put("recipientPlayerIds", List.of(bobId.toString()));
            setup.s1().send("/app/session/" + setup.host().sessionId() + "/item-share-partial", body);

            List<String> partialFrames = awaitFrames(setup.topicFrames(), "\"ITEM_SHARED_PARTIAL\"", 3, 5000);

            // 브로드캐스트 프레임에 recipients 배열 확인 (UUID[] 직렬화 검증)
            JsonNode partialPayload = mapper.readTree(partialFrames.get(0)).path("payload");
            JsonNode recipients = partialPayload.path("recipients");
            assertThat(recipients.isArray()).isTrue();
            assertThat(recipients.size()).isEqualTo(1);
            assertThat(recipients.get(0).path("playerId").asText()).isEqualTo(bobId.toString());

            // Bob 만 CLUE_DELIVERED (source=share_partial)
            String bobDelivery = awaitFrame(setup.privateFrames2(), "CLUE_DELIVERED", 3000);
            assertThat(bobDelivery).contains("\"source\":\"share_partial\"");
            // Charlie — 수신 없음
            assertNoFrame(setup.privateFrames3(), "CLUE_DELIVERED", 200);

            // DB: Bob ACL 있음, Charlie ACL 없음
            assertThat(clueAclRepository.findById(new ClueAclId(clueX.getId(), bobId)))
                .isPresent().get().extracting(ClueAcl::getSource).isEqualTo("share_partial");
            assertThat(clueAclRepository.findById(new ClueAclId(clueX.getId(), charlieId))).isEmpty();

            // DB: 소유권 불변
            assertThat(clueRepository.findById(clueX.getId()))
                .isPresent().get()
                .extracting(Clue::getCurrentOwnerPlayerId).isEqualTo(aliceId);

            // DB: item_actions — recipientPlayerIds UUID[] 왕복 핵심 검증 (이 세션 한정)
            var actions = itemActionRepository.findAll().stream()
                .filter(a -> a.getSessionId().equals(sessId)).toList();
            assertThat(actions).hasSize(1);
            ItemAction action = actions.get(0);
            assertThat(action.getActionType()).isEqualTo("share_partial");
            assertThat(action.getActorPlayerId()).isEqualTo(aliceId);
            assertThat(action.getActorClueId()).isEqualTo(clueX.getId());
            assertThat(action.getTargetPlayerId()).isNull();
            assertThat(action.getTargetClueId()).isNull();
            assertThat(action.getRecipientPlayerIds())
                .isNotNull()
                .hasSize(1)
                .containsExactly(bobId);

        } finally {
            setup.disconnect();
        }
    }
}
