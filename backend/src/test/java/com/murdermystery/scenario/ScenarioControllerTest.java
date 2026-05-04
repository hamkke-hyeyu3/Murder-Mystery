package com.murdermystery.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScenarioControllerTest {

    private MockMvc mvc;
    private final ScenarioRepository repo = mock(ScenarioRepository.class);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ScenarioController(repo)).build();
    }

    private Scenario sampleScenario() {
        return new Scenario(
            "toy-manor", "Toy Manor 살인 사건", "summary", "🏚️", 60,
            List.of(
                new ScenarioCharacter("alice", "앨리스"),
                new ScenarioCharacter("bob", "밥"),
                new ScenarioCharacter("charlie", "찰리")),
            List.of(new ScenarioLocation("loc1", "도서관", null, null)),
            List.of("loc1"),
            List.of(new ScenarioItem("i1", "편지", "loc1")),
            "bob", true, 3
        );
    }

    @Test
    void get_returns_catalog_summary() throws Exception {
        when(repo.findAll()).thenReturn(List.of(sampleScenario()));

        mvc.perform(get("/api/scenarios").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("toy-manor"))
            .andExpect(jsonPath("$[0].title").value("Toy Manor 살인 사건"))
            .andExpect(jsonPath("$[0].icon").value("🏚️"))
            .andExpect(jsonPath("$[0].playerCount").value(3))
            .andExpect(jsonPath("$[0].estimatedMinutes").value(60));
    }

    @Test
    void get_returns_empty_array_when_no_scenarios() throws Exception {
        when(repo.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/scenarios").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
