package com.murdermystery.scenario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioRepository scenarioRepository;

    public ScenarioController(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    @GetMapping
    public List<ScenarioSummary> list() {
        return scenarioRepository.findAll().stream()
            .map(s -> new ScenarioSummary(
                s.id(), s.title(), s.icon(), s.summary(),
                s.characters().size(), s.estimatedMinutes()))
            .toList();
    }
}
