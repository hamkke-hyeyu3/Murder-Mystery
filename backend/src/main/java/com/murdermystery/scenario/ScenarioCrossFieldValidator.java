package com.murdermystery.scenario;

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

        return Optional.empty();
    }
}
