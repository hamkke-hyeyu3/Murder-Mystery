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
@ActiveProfiles({"test", "dev"})
@Import(TestcontainersConfiguration.class)
class VoteIntegrationTest {

    @LocalServerPort int port;
    @Autowired VoteService voteService;
    @Autowired VoteRepository voteRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired RoundTurnService roundTurnService;
    @Autowired RoundLifecycleService roundLifecycleService;

    private RestTemplate http;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() { http = new RestTemplate(); }

    @AfterEach
    void tearDown() {
        voteService.cancelPendingForTest();
        roundTurnService.cancelPendingAutoSelectsForTest();
        roundLifecycleService.cancelPendingDeadlinesForTest();
    }

    // ── STOMP helpers ──────────────────────────────────────────────────

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

    // ── Game setup helpers ─────────────────────────────────────────────

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
        BlockingQueue<String> topicFrames
    ) {
        void disconnect() {
            try { s1.disconnect(); } catch (Exception ignored) {}
            try { s2.disconnect(); } catch (Exception ignored) {}
            c1.stop(); c2.stop();
        }
    }

    /** Drives a 2-player dev-duo game to the first TURN_STARTED. */
    private DuoSetup setupToRoundDuo() throws Exception {
        PlayerInfo host  = createHostWithDevDuo();
        PlayerInfo guest = joinGuest(host.inviteCode(), "bob");

        WebSocketStompClient c1 = buildStompClient();
        WebSocketStompClient c2 = buildStompClient();

        BlockingQueue<String> topicFrames = new LinkedBlockingQueue<>();
        CountDownLatch cardLatch = new CountDownLatch(2);
        CountDownLatch turnLatch = new CountDownLatch(1);

        StompSession s1 = connectAuthenticated(c1, host.inviteCode(), "alice", host.playerId());
        StompSession s2 = connectAuthenticated(c2, host.inviteCode(), "bob",   guest.playerId());

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

        String privateDest = "/user/queue/session/" + host.sessionId() + "/private";
        StompFrameHandler cardHandler1 = new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                if (payload != null && payload.toString().contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        };
        StompFrameHandler cardHandler2 = new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                if (payload != null && payload.toString().contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        };
        s1.subscribe(privateDest, cardHandler1);
        s2.subscribe(privateDest, cardHandler2);

        Thread.sleep(300);
        postStart(host);

        assertThat(cardLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for CHARACTER_CARD_DEALT × 2").isTrue();

        awaitFrame(topicFrames, "\"tutorial\"", 3000);

        postTutorialAck(host.sessionId(), host.deviceId());
        postTutorialAck(host.sessionId(), guest.deviceId());

        assertThat(turnLatch.await(5, TimeUnit.SECONDS))
            .withFailMessage("Timed out waiting for TURN_STARTED").isTrue();

        return new DuoSetup(host, guest, s1, s2, c1, c2, topicFrames);
    }

    /**
     * Drives the duo game through all 2 rounds (4 turns total) to VOTE_STARTED.
     * dev-duo: round_count=2, 2 players = 2 turns per round.
     */
    private DuoSetup setupToVote() throws Exception {
        DuoSetup setup = setupToRoundDuo();
        UUID sessId = UUID.fromString(setup.host().sessionId());

        roundTurnService.autoSelectTurn(sessId, 1, 0, 2);
        roundTurnService.autoSelectTurn(sessId, 1, 1, 2);

        awaitFrame(setup.topicFrames(), "ROUND_STARTED", 5000);
        awaitFrame(setup.topicFrames(), "TURN_STARTED",  5000);

        roundTurnService.autoSelectTurn(sessId, 2, 0, 2);
        roundTurnService.autoSelectTurn(sessId, 2, 1, 2);

        awaitFrame(setup.topicFrames(), "VOTE_STARTED", 5000);
        return setup;
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void singleWinner_bothVoteSameCharacter() throws Exception {
        DuoSetup setup = setupToVote();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            voteService.submit(sessId, hostId,  "host", 0, setup.host().inviteCode());
            voteService.submit(sessId, guestId, "host", 0, setup.host().inviteCode());

            String resultFrame = awaitFrame(setup.topicFrames(), "VOTE_RESULT", 3000);
            assertThat(resultFrame).contains("single_winner");

            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getVoteOutcome()).isEqualTo("single_winner");
            assertThat(session.getVoteWinnerCharacterId()).isEqualTo("host");

            assertThat(voteRepository.findBySessionIdAndRoundNo(sessId, 0)).hasSize(2);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void tie_thenRunoff_singleWinner() throws Exception {
        DuoSetup setup = setupToVote();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            // Round 0: tie
            voteService.submit(sessId, hostId,  "host",  0, setup.host().inviteCode());
            voteService.submit(sessId, guestId, "guest", 0, setup.host().inviteCode());

            String runoffFrame = awaitFrame(setup.topicFrames(), "RUNOFF_STARTED", 3000);
            assertThat(runoffFrame).contains("\"roundNo\":1");

            // Round 1: both vote "guest" → single winner
            voteService.submit(sessId, hostId,  "guest", 1, setup.host().inviteCode());
            voteService.submit(sessId, guestId, "guest", 1, setup.host().inviteCode());

            String resultFrame = awaitFrame(setup.topicFrames(), "VOTE_RESULT", 3000);
            assertThat(resultFrame).contains("single_winner");

            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getVoteOutcome()).isEqualTo("single_winner");
            assertThat(session.getVoteWinnerCharacterId()).isEqualTo("guest");

            assertThat(voteRepository.findBySessionIdAndRoundNo(sessId, 0)).hasSize(2);
            assertThat(voteRepository.findBySessionIdAndRoundNo(sessId, 1)).hasSize(2);

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void tie_thenRunoff_failed() throws Exception {
        DuoSetup setup = setupToVote();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            // Round 0: tie
            voteService.submit(sessId, hostId,  "host",  0, setup.host().inviteCode());
            voteService.submit(sessId, guestId, "guest", 0, setup.host().inviteCode());

            awaitFrame(setup.topicFrames(), "RUNOFF_STARTED", 3000);

            // Round 1: tie again
            voteService.submit(sessId, hostId,  "host",  1, setup.host().inviteCode());
            voteService.submit(sessId, guestId, "guest", 1, setup.host().inviteCode());

            String resultFrame = awaitFrame(setup.topicFrames(), "VOTE_RESULT", 3000);
            assertThat(resultFrame).contains("failed");

            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getVoteOutcome()).isEqualTo("failed");
            assertThat(session.getVoteWinnerCharacterId()).isNull();

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void submit_upsert_secondSubmitUpdatesTarget() throws Exception {
        DuoSetup setup = setupToVote();
        try {
            UUID sessId = UUID.fromString(setup.host().sessionId());
            UUID hostId = UUID.fromString(setup.host().playerId());

            // Submit once, then change vote (UPSERT)
            voteService.submit(sessId, hostId, "host",  0, setup.host().inviteCode());
            voteService.submit(sessId, hostId, "guest", 0, setup.host().inviteCode());

            List<Vote> votes = voteRepository.findBySessionIdAndRoundNo(sessId, 0);
            List<Vote> myVotes = votes.stream()
                .filter(v -> v.getVoterPlayerId().equals(hostId)).toList();

            assertThat(myVotes).hasSize(1);
            assertThat(myVotes.get(0).getTargetCharacterId()).isEqualTo("guest");

        } finally {
            setup.disconnect();
        }
    }
}
