package com.murdermystery.scenario;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class ScenarioRepository {

    private List<Scenario> scenarios = List.of();

    @PostConstruct
    public void init() throws IOException {
        this.scenarios = new ScenarioLoader("classpath:scenarios/*.json").loadAll();
    }

    public List<Scenario> findAll() {
        return scenarios;
    }

    public Optional<Scenario> findById(String id) {
        return scenarios.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
