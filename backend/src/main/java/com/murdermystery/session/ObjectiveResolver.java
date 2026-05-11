package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;

import java.util.Optional;

class ObjectiveResolver {

    private ObjectiveResolver() {}

    static Optional<String> resolve(Scenario scenario, String characterId, int roundNumber) {
        if (scenario == null || characterId == null || scenario.characters() == null) {
            return Optional.empty();
        }
        return scenario.characters().stream()
            .filter(c -> characterId.equals(c.id()) && c.objectivesByRound() != null)
            .findFirst()
            .flatMap(c -> c.objectivesByRound().stream()
                .filter(o -> o.round() == roundNumber)
                .findFirst()
                .map(o -> o.text()));
    }
}
