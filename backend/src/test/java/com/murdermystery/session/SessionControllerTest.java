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
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new SessionController(service, joinService, resumeService))
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
                    new PlayerSummary("p2", "bob", false))
        );
    }

    @Test
    void get_happyPath_returnsAllFieldsIncludingCounts() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.getSession(sessionId)).thenReturn(sampleViewResponse(sessionId.toString()));

        mvc.perform(get("/api/sessions/" + sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
            .andExpect(jsonPath("$.inviteCode").value("123456"))
            .andExpect(jsonPath("$.requiredCharacterCount").value(3))
            .andExpect(jsonPath("$.joinedCount").value(2))
            .andExpect(jsonPath("$.players").isArray())
            .andExpect(jsonPath("$.players.length()").value(2));
    }

    @Test
    void get_unknownSessionId_returns404() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.getSession(sessionId)).thenThrow(new SessionNotFoundException(sessionId.toString()));

        mvc.perform(get("/api/sessions/" + sessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("session not found"));
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        mvc.perform(get("/api/sessions/not-a-uuid"))
            .andExpect(status().isBadRequest());
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
