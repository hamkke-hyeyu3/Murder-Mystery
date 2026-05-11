package com.murdermystery.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murdermystery.web.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    private MockMvc mvc;
    private final SessionService service = mock(SessionService.class);
    private final JoinService joinService = mock(JoinService.class);
    private final ResumeService resumeService = mock(ResumeService.class);
    private final StartGameService startGameService = mock(StartGameService.class);
    private final TutorialService tutorialService = mock(TutorialService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new SessionController(service, joinService, resumeService, startGameService, tutorialService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    private CreateSessionResponse sampleResponse() {
        return new CreateSessionResponse(
            "sess-uuid-1", "123456", "toy-manor", "alice", "lobby", "player-uuid-1"
        );
    }

    @Test
    void post_happyPath_returns200WithAllFields() throws Exception {
        when(service.createSession(eq("toy-manor"), eq("alice"), any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("toy-manor", "alice"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("sess-uuid-1"))
            .andExpect(jsonPath("$.inviteCode").value("123456"))
            .andExpect(jsonPath("$.scenarioId").value("toy-manor"))
            .andExpect(jsonPath("$.hostNickname").value("alice"))
            .andExpect(jsonPath("$.phase").value("lobby"))
            .andExpect(jsonPath("$.playerId").value("player-uuid-1"));
    }

    @Test
    void post_blankHostNickname_returns400() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("toy-manor", ""))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_blankScenarioId_returns400() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("", "alice"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_unknownScenario_returns400() throws Exception {
        when(service.createSession(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("unknown scenario"));

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("unknown", "alice"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("unknown scenario"));
    }

    private JoinResponse sampleJoinResponse() {
        return new JoinResponse(
            "sess-uuid-1", "123456", "toy-manor", "lobby", "bob", "player-uuid-2",
            List.of(new PlayerSummary("player-uuid-1", "alice", true),
                    new PlayerSummary("player-uuid-2", "bob", false))
        );
    }

    @Test
    void post_join_happyPath_returns200WithBody() throws Exception {
        when(joinService.join(eq("123456"), eq("bob"), any())).thenReturn(sampleJoinResponse());

        mvc.perform(post("/api/sessions/123456/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JoinRequest("bob"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value("sess-uuid-1"))
            .andExpect(jsonPath("$.inviteCode").value("123456"))
            .andExpect(jsonPath("$.nickname").value("bob"))
            .andExpect(jsonPath("$.players").isArray());
    }

    @Test
    void post_join_unknownInvite_returns400() throws Exception {
        when(joinService.join(any(), any(), any())).thenThrow(new InviteCodeNotFoundException("999999"));

        mvc.perform(post("/api/sessions/999999/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JoinRequest("bob"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_join_takenNickname_returns409() throws Exception {
        when(joinService.join(any(), any(), any())).thenThrow(new NicknameTakenException("bob"));

        mvc.perform(post("/api/sessions/123456/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JoinRequest("bob"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("nickname already taken"));
    }

    @Test
    void post_join_phaseInProgress_returns409() throws Exception {
        when(joinService.join(any(), any(), any())).thenThrow(new SessionNotJoinableException("in_progress"));

        mvc.perform(post("/api/sessions/123456/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JoinRequest("bob"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("session not in lobby"));
    }

    @Test
    void post_join_blankNickname_returns400() throws Exception {
        mvc.perform(post("/api/sessions/123456/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new JoinRequest(""))))
            .andExpect(status().isBadRequest());
    }

    private SessionViewResponse sampleViewResponse(String sessionId) {
        return new SessionViewResponse(
            sessionId, "123456", "toy-manor", "lobby",
            3, 2,
            List.of(new PlayerSummary("p1", "alice", true),
                    new PlayerSummary("p2", "bob", false)),
            null, null, null, null, null, List.of(), null
        );
    }

    private SessionViewResponse sampleInProgressViewResponse(String sessionId) {
        SessionViewResponse.RoundView round = new SessionViewResponse.RoundView(
            1, "자신을 소개해주세요", "공통 힌트", 1000L, 61000L
        );
        SessionViewResponse.ObjectiveView objective = new SessionViewResponse.ObjectiveView(
            1, 3, "진실을 밝혀라"
        );
        SessionViewResponse.CharacterCardView character = new SessionViewResponse.CharacterCardView(
            "char-a", "알리스", 0, null, null, null, null, null, null, null, List.of()
        );
        SessionViewResponse.MeView me = new SessionViewResponse.MeView(
            "p1", "alice", true, "char-a", character, objective, null, List.of()
        );
        return new SessionViewResponse(
            sessionId, "123456", "toy-manor", "in_progress",
            3, 3,
            List.of(new PlayerSummary("p1", "alice", true)),
            "round", 1, List.of("char-a", "char-b", "char-c"),
            round, null, List.of(), me
        );
    }

    @Test
    void get_happyPath_returnsAllFieldsIncludingCounts() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.getSession(eq(sessionId), any())).thenReturn(sampleViewResponse(sessionId.toString()));

        mvc.perform(get("/api/sessions/" + sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
            .andExpect(jsonPath("$.inviteCode").value("123456"))
            .andExpect(jsonPath("$.requiredCharacterCount").value(3))
            .andExpect(jsonPath("$.joinedCount").value(2))
            .andExpect(jsonPath("$.players").isArray())
            .andExpect(jsonPath("$.players.length()").value(2))
            .andExpect(jsonPath("$.state").doesNotExist())
            .andExpect(jsonPath("$.round").doesNotExist())
            .andExpect(jsonPath("$.me").doesNotExist());
    }

    @Test
    void get_inProgress_withDeviceId_includesSnapshotFields() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(service.getSession(eq(sessionId), eq(deviceId)))
            .thenReturn(sampleInProgressViewResponse(sessionId.toString()));

        mvc.perform(get("/api/sessions/" + sessionId)
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phase").value("in_progress"))
            .andExpect(jsonPath("$.state").value("round"))
            .andExpect(jsonPath("$.currentRoundNumber").value(1))
            .andExpect(jsonPath("$.round.prompt").value("자신을 소개해주세요"))
            .andExpect(jsonPath("$.round.deadlineAt").value(61000))
            .andExpect(jsonPath("$.me.playerId").value("p1"))
            .andExpect(jsonPath("$.me.character.characterId").value("char-a"))
            .andExpect(jsonPath("$.me.objective.objective").value("진실을 밝혀라"));
    }

    @Test
    void get_unknownSessionId_returns404() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.getSession(eq(sessionId), any())).thenThrow(new SessionNotFoundException(sessionId.toString()));

        mvc.perform(get("/api/sessions/" + sessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("session not found"));
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        mvc.perform(get("/api/sessions/not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    private StartGameResponse sampleStartResponse(UUID sessionId) {
        return new StartGameResponse(sessionId, "in_progress", "intro", List.of("char-a", "char-b", "char-c"));
    }

    @Test
    void post_start_happyPath_returns200() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(startGameService.start(eq(sessionId), eq(deviceId))).thenReturn(sampleStartResponse(sessionId));

        mvc.perform(post("/api/sessions/" + sessionId + "/start")
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phase").value("in_progress"))
            .andExpect(jsonPath("$.state").value("intro"));
    }

    @Test
    void post_start_missingDeviceId_returns400() throws Exception {
        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/start"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_start_notHost_returns403() throws Exception {
        when(startGameService.start(any(), any())).thenThrow(new NotHostException());

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/start")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("host only"));
    }

    @Test
    void post_start_alreadyStarted_returns409() throws Exception {
        when(startGameService.start(any(), any())).thenThrow(new SessionAlreadyStartedException());

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/start")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("session already started"));
    }

    @Test
    void post_start_concurrentStart_returns409() throws Exception {
        when(startGameService.start(any(), any()))
            .thenThrow(new ObjectOptimisticLockingFailureException(Session.class, "sess-id"));

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/start")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("session already started"));
    }

    @Test
    void post_start_countMismatch_returns409() throws Exception {
        when(startGameService.start(any(), any())).thenThrow(new LobbyCountMismatchException(2, 3));

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/start")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.joined").value(2))
            .andExpect(jsonPath("$.required").value(3));
    }

    @Test
    void post_tutorialAck_missingDeviceId_returns400() throws Exception {
        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/tutorial-ack"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void post_tutorialAck_happyPath_returnsAckResponse() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(tutorialService.acknowledge(eq(sessionId), eq(deviceId)))
            .thenReturn(new TutorialAckResponse(2, 3, "tutorial"));

        mvc.perform(post("/api/sessions/" + sessionId + "/tutorial-ack")
                .header("X-Device-Id", deviceId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acked").value(2))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.state").value("tutorial"));
    }

    @Test
    void post_tutorialAck_wrongPhase_returns409() throws Exception {
        when(tutorialService.acknowledge(any(), any())).thenThrow(new TutorialPhaseRequiredException());

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/tutorial-ack")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("tutorial phase required"));
    }

    @Test
    void post_tutorialAck_playerNotInSession_returns403() throws Exception {
        when(tutorialService.acknowledge(any(), any())).thenThrow(new PlayerNotInSessionException());

        mvc.perform(post("/api/sessions/" + UUID.randomUUID() + "/tutorial-ack")
                .header("X-Device-Id", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("player not in session"));
    }

    @Test
    void post_codeExhaustion_returns500WithGenericMessage() throws Exception {
        when(service.createSession(any(), any(), any()))
            .thenThrow(new IllegalStateException("invite code exhausted"));

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("toy-manor", "alice"))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("잠시 후 다시 시도해주세요"));
    }
}
