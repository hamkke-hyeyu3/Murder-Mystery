package com.murdermystery.session;

import com.murdermystery.scenario.Round;
import com.murdermystery.scenario.RoundObjective;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectiveResolverTest {

    private Scenario scenarioWith(String charId, List<RoundObjective> objectives) {
        return new Scenario(
            "s1", "Test", "summary", "icon", 60,
            List.of(new ScenarioCharacter(charId, "캐릭터", null, null, null, null, null, null, null, objectives, null)),
            List.of(), List.of(), List.of(), charId, false, 3,
            List.of(new Round("p1", null, 60), new Round("p2", null, 60), new Round("p3", null, 60))
        );
    }

    @Test
    void resolve_matchingRound_returnsObjective() {
        Scenario scenario = scenarioWith("char-a", List.of(
            new RoundObjective(1, "라운드1 목표"),
            new RoundObjective(2, "라운드2 목표")
        ));

        assertThat(ObjectiveResolver.resolve(scenario, "char-a", 1)).isEqualTo(Optional.of("라운드1 목표"));
        assertThat(ObjectiveResolver.resolve(scenario, "char-a", 2)).isEqualTo(Optional.of("라운드2 목표"));
    }

    @Test
    void resolve_noMatchingRound_returnsEmpty() {
        Scenario scenario = scenarioWith("char-a", List.of(new RoundObjective(1, "목표")));

        assertThat(ObjectiveResolver.resolve(scenario, "char-a", 3)).isEmpty();
    }

    @Test
    void resolve_unknownCharacter_returnsEmpty() {
        Scenario scenario = scenarioWith("char-a", List.of(new RoundObjective(1, "목표")));

        assertThat(ObjectiveResolver.resolve(scenario, "char-z", 1)).isEmpty();
    }

    @Test
    void resolve_nullObjectivesByRound_returnsEmpty() {
        Scenario scenario = scenarioWith("char-a", null);

        assertThat(ObjectiveResolver.resolve(scenario, "char-a", 1)).isEmpty();
    }

    @Test
    void resolve_nullScenario_returnsEmpty() {
        assertThat(ObjectiveResolver.resolve(null, "char-a", 1)).isEmpty();
    }

    @Test
    void resolve_nullCharacterId_returnsEmpty() {
        Scenario scenario = scenarioWith("char-a", List.of(new RoundObjective(1, "목표")));
        assertThat(ObjectiveResolver.resolve(scenario, null, 1)).isEmpty();
    }
}
