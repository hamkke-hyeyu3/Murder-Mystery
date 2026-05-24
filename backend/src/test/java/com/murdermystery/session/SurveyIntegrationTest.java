package com.murdermystery.session;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = {
    "app.reveal.duration-ms=200",
    "app.ending.debrief-delay-ms=200",
    "app.ending.survey-delay-ms=200",
    "app.session.survey-timeout-ms=2000"
})
class SurveyIntegrationTest {

    @LocalServerPort int port;
    @Autowired RevealService revealService;
    @Autowired VoteService voteService;
    @Autowired RoundTurnService roundTurnService;
    @Autowired RoundLifecycleService roundLifecycleService;
    @Autowired EndingService endingService;
    @Autowired MissionService missionService;
    @Autowired SurveyService surveyService;
    @Autowired SurveyResponseRepository surveyResponseRepository;
    @Autowired SessionRepository sessionRepository;

    private RestTemplate http;

    @BeforeEach void setUp() { http = new RestTemplate(); }

    @AfterEach void tearDown() {
        revealService.cancelPendingForTest();
        voteService.cancelPendingForTest();
        roundTurnService.cancelPendingAutoSelectsForTest();
        roundLifecycleService.cancelPendingDeadlinesForTest();
        endingService.cancelPendingForTest();
        surveyService.clearEndedSessionsForTest();
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

    /** Drives a 2-player game through all rounds to MISSION_PHASE_STARTED. */
    private DuoSetup setupToMission() throws Exception {
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

        StompFrameHandler cardHandler = new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
            @Override public void handleFrame(StompHeaders h, Object payload) {
                if (payload != null && payload.toString().contains("CHARACTER_CARD_DEALT")) cardLatch.countDown();
            }
        };
        s1.subscribe(privateDest, cardHandler);
        s2.subscribe(privateDest, cardHandler);

        Thread.sleep(300);
        postStart(host);
        assertThat(cardLatch.await(5, TimeUnit.SECONDS)).isTrue();

        awaitFrame(topicFrames, "\"tutorial\"", 3000);
        postTutorialAck(host.sessionId(), host.deviceId());
        postTutorialAck(host.sessionId(), guest.deviceId());
        assertThat(turnLatch.await(5, TimeUnit.SECONDS)).isTrue();
        topicFrames.clear(); // discard stale round-1 frames so awaitFrame("ROUND_STARTED") below matches round 2

        UUID sessId = UUID.fromString(host.sessionId());
        roundTurnService.autoSelectTurn(sessId, 1, 0, 2);
        roundTurnService.autoSelectTurn(sessId, 1, 1, 2);

        awaitFrame(topicFrames, "ROUND_STARTED", 5000);
        awaitFrame(topicFrames, "TURN_STARTED",  3000);

        roundTurnService.autoSelectTurn(sessId, 2, 0, 2);
        roundTurnService.autoSelectTurn(sessId, 2, 1, 2);

        awaitFrame(topicFrames, "VOTE_STARTED", 5000);

        // Single winner vote
        UUID hostId  = UUID.fromString(host.playerId());
        UUID guestId = UUID.fromString(guest.playerId());
        voteService.submit(sessId, hostId,  "host", 0, host.inviteCode());
        voteService.submit(sessId, guestId, "host", 0, host.inviteCode());

        awaitFrame(topicFrames, "MISSION_PHASE_STARTED", 5000);

        return new DuoSetup(host, guest, s1, s2, c1, c2, topicFrames);
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void allSubmit_sessionEnded_dbRowsAndStateVerified() throws Exception {
        DuoSetup setup = setupToMission();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            // Complete mission checks → triggers ending chain
            missionService.checkComplete(sessId, hostId);
            missionService.checkComplete(sessId, guestId);

            // Wait for survey state
            awaitFrame(setup.topicFrames(), "SURVEY_AVAILABLE", 5000);

            // Both players submit survey (with scores)
            var req = new com.murdermystery.ws.SurveySubmitRequest(5, 4, "fun game");
            surveyService.submit(sessId, hostId, req);
            surveyService.submit(sessId, guestId, req);

            // SESSION_ENDED broadcast — must arrive before survey-timeout-ms(2000) fires
            awaitFrame(setup.topicFrames(), "SESSION_ENDED", 1000);

            // DB: 2 survey_responses rows
            long count = surveyResponseRepository.countBySessionId(sessId);
            assertThat(count).isEqualTo(2);

            // DB: session state='ended' and phase='ended'
            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getState()).isEqualTo("ended");
            assertThat(session.getPhase()).isEqualTo("ended");

        } finally {
            setup.disconnect();
        }
    }

    @Test
    void surveyTimeout_sessionEndedAutomatically() throws Exception {
        DuoSetup setup = setupToMission();
        try {
            UUID sessId  = UUID.fromString(setup.host().sessionId());
            UUID hostId  = UUID.fromString(setup.host().playerId());
            UUID guestId = UUID.fromString(setup.guest().playerId());

            missionService.checkComplete(sessId, hostId);
            missionService.checkComplete(sessId, guestId);

            awaitFrame(setup.topicFrames(), "SURVEY_AVAILABLE", 5000);

            // Only one player submits — timeout (400ms) fires for the other
            surveyService.submit(sessId, hostId,
                new com.murdermystery.ws.SurveySubmitRequest(null, null, null));

            // SESSION_ENDED via timeout (survey-timeout-ms=2000ms)
            awaitFrame(setup.topicFrames(), "SESSION_ENDED", 5000);

            Session session = sessionRepository.findById(sessId).orElseThrow();
            assertThat(session.getState()).isEqualTo("ended");

        } finally {
            setup.disconnect();
        }
    }
}
