package com.murdermystery.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCrossFieldValidatorTest {

    private final ScenarioCrossFieldValidator validator = new ScenarioCrossFieldValidator();

    private Scenario validScenario() {
        return new Scenario(
            "id", "title", "summary", null, 30,
            List.of(
                new ScenarioCharacter("a", "A"),
                new ScenarioCharacter("b", "B"),
                new ScenarioCharacter("c", "C")),
            List.of(
                new ScenarioLocation("loc1", "L1", null, null),
                new ScenarioLocation("loc2", "L2", null, null),
                new ScenarioLocation("loc3", "L3", null, null)),
            List.of("loc1", "loc2", "loc3"),
            List.of(
                new ScenarioItem("i1", "I1", "loc1"),
                new ScenarioItem("i2", "I2", "loc2"),
                new ScenarioItem("i3", "I3", "loc3")),
            "c", false, 2
        );
    }

    @Test
    void valid_scenario_passes() {
        assertThat(validator.validate(validScenario())).isEmpty();
    }

    @Test
    void unknown_pool_location_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30,
            List.of(
                new ScenarioCharacter("a", "A"),
                new ScenarioCharacter("b", "B"),
                new ScenarioCharacter("c", "C")),
            List.of(
                new ScenarioLocation("loc1", "L1", null, null),
                new ScenarioLocation("loc2", "L2", null, null),
                new ScenarioLocation("loc3", "L3", null, null)),
            List.of("loc1", "loc2", "unknown"),
            List.of(
                new ScenarioItem("i1", "I1", "loc1"),
                new ScenarioItem("i2", "I2", "loc2"),
                new ScenarioItem("i3", "I3", "loc3")),
            "c", false, 2
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void pool_smaller_than_characters_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30,
            List.of(
                new ScenarioCharacter("a", "A"),
                new ScenarioCharacter("b", "B"),
                new ScenarioCharacter("c", "C")),
            List.of(
                new ScenarioLocation("loc1", "L1", null, null),
                new ScenarioLocation("loc2", "L2", null, null)),
            List.of("loc1", "loc2"),
            List.of(
                new ScenarioItem("i1", "I1", "loc1"),
                new ScenarioItem("i2", "I2", "loc2")),
            "c", false, 2
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void location_without_origin_item_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30,
            List.of(
                new ScenarioCharacter("a", "A"),
                new ScenarioCharacter("b", "B"),
                new ScenarioCharacter("c", "C")),
            List.of(
                new ScenarioLocation("loc1", "L1", null, null),
                new ScenarioLocation("loc2", "L2", null, null),
                new ScenarioLocation("loc3", "L3", null, null)),
            List.of("loc1", "loc2", "loc3"),
            List.of(
                new ScenarioItem("i1", "I1", "loc1"),
                new ScenarioItem("i3", "I3", "loc3")), // loc2 에 origin item 없음
            "c", false, 2
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void unknown_culprit_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30,
            List.of(
                new ScenarioCharacter("a", "A"),
                new ScenarioCharacter("b", "B"),
                new ScenarioCharacter("c", "C")),
            List.of(
                new ScenarioLocation("loc1", "L1", null, null),
                new ScenarioLocation("loc2", "L2", null, null),
                new ScenarioLocation("loc3", "L3", null, null)),
            List.of("loc1", "loc2", "loc3"),
            List.of(
                new ScenarioItem("i1", "I1", "loc1"),
                new ScenarioItem("i2", "I2", "loc2"),
                new ScenarioItem("i3", "I3", "loc3")),
            "nobody", false, 2
        );
        assertThat(validator.validate(s)).isPresent();
    }
}
