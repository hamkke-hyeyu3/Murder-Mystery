package com.murdermystery.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.murdermystery.web.RestExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    private MockMvc mvc;
    private final SessionService service = mock(SessionService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new SessionController(service))
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
        when(service.createSession("toy-manor", "alice")).thenReturn(sampleResponse());

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
        when(service.createSession(any(), any()))
            .thenThrow(new IllegalArgumentException("unknown scenario"));

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("unknown", "alice"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("unknown scenario"));
    }

    @Test
    void post_codeExhaustion_returns500WithGenericMessage() throws Exception {
        when(service.createSession(any(), any()))
            .thenThrow(new IllegalStateException("invite code exhausted"));

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateSessionRequest("toy-manor", "alice"))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("잠시 후 다시 시도해주세요"));
    }
}
