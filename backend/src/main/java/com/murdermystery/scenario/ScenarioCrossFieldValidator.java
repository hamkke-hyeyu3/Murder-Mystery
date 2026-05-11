package com.murdermystery.scenario;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ScenarioCrossFieldValidator {

    public Optional<String> validate(Scenario scenario) {
        Set<String> locationIds = scenario.locations().stream()
            .map(ScenarioLocation::id)
            .collect(Collectors.toSet());

        for (String poolId : scenario.locationPool()) {
            if (!locationIds.contains(poolId)) {
                return Optional.of("location_pool contains unknown location id: " + poolId);
            }
        }

        if (scenario.locationPool().size() < scenario.characters().size()) {
            return Optional.of(
                "location_pool.size()=" + scenario.locationPool().size()
                + " < characters.size()=" + scenario.characters().size()
            );
        }

        for (String poolId : scenario.locationPool()) {
            boolean hasItem = scenario.items().stream()
                .anyMatch(item -> poolId.equals(item.originLocationId()));
            if (!hasItem) {
                return Optional.of("no item with origin_location_id=" + poolId);
            }
        }

        Set<String> characterIds = scenario.characters().stream()
            .map(ScenarioCharacter::id)
            .collect(Collectors.toSet());
        if (!characterIds.contains(scenario.trueCulpritCharacterId())) {
            return Optional.of(
                "true_culprit_character_id=" + scenario.trueCulpritCharacterId()
                + " not found in characters"
            );
        }

        if (scenario.rounds() == null) {
            return Optional.of("rounds is required — scenario has round_count=" + scenario.roundCount() + " but no rounds defined");
        }
        if (scenario.rounds().size() != scenario.roundCount()) {
            return Optional.of(
                "rounds.size()=" + scenario.rounds().size()
                + " != round_count=" + scenario.roundCount()
            );
        }
        for (int i = 1; i < scenario.rounds().size(); i++) {
            Round r = scenario.rounds().get(i);
            if (r.prompt() == null || r.prompt().isBlank()) {
                return Optional.of("rounds[" + i + "] (round " + (i + 1) + ") has blank prompt — required for k>=2");
            }
        }

        int totalRounds = scenario.roundCount();
        for (ScenarioCharacter ch : scenario.characters()) {
            List<RoundObjective> objectives = ch.objectivesByRound();
            if (objectives == null || objectives.isEmpty()) continue;

            Set<Integer> roundNums = objectives.stream()
                .map(RoundObjective::round)
                .collect(Collectors.toSet());
            if (roundNums.size() != objectives.size()) {
                return Optional.of("character '" + ch.id() + "' has duplicate round in objectives_by_round");
            }
            for (RoundObjective obj : objectives) {
                if (obj.round() < 1 || obj.round() > totalRounds) {
                    return Optional.of("character '" + ch.id() + "' objective round=" + obj.round()
                        + " is out of range [1.." + totalRounds + "]");
                }
            }
            if (roundNums.size() != totalRounds) {
                return Optional.of("character '" + ch.id() + "' objectives_by_round covers "
                    + roundNums.size() + " rounds but round_count=" + totalRounds);
            }
        }

        for (ScenarioCharacter ch : scenario.characters()) {
            if (ch.alibiLocationId() != null && !locationIds.contains(ch.alibiLocationId())) {
                return Optional.of("character '" + ch.id() + "' alibi_location_id='"
                    + ch.alibiLocationId() + "' not found in locations");
            }
        }

        return Optional.empty();
    }
}
