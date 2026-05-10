package com.murdermystery.scenario;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ScenarioRepository {

    private final List<String> patterns;
    private final List<String> devPatterns;
    private List<Scenario> scenarios = List.of();

    public ScenarioRepository(
            @Value("${app.scenarios.patterns}") List<String> patterns,
            @Value("${app.scenarios.dev-patterns:}") List<String> devPatterns) {
        this.patterns = patterns;
        this.devPatterns = devPatterns;
    }

    @PostConstruct
    public void init() throws IOException {
        List<Scenario> all = new ArrayList<>();
        for (String pattern : patterns) {
            all.addAll(new ScenarioLoader(pattern.strip()).loadAll());
        }
        for (String pattern : devPatterns) {
            all.addAll(new ScenarioLoader(pattern.strip(), "scenario-schema-dev.json").loadAll());
        }
        this.scenarios = List.copyOf(all);
    }

    public List<Scenario> findAll() {
        return scenarios;
    }

    public Optional<Scenario> findById(String id) {
        return scenarios.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
