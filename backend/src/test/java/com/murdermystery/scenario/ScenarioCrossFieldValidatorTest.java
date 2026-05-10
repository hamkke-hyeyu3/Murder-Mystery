package com.murdermystery.scenario;

import org.junit.jupiter.api.Test;
import com.murdermystery.scenario.Round;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioCrossFieldValidatorTest {

    private final ScenarioCrossFieldValidator validator = new ScenarioCrossFieldValidator();

    private static final List<ScenarioCharacter> CHARS_3 = List.of(
        new ScenarioCharacter("a", "A"),
        new ScenarioCharacter("b", "B"),
        new ScenarioCharacter("c", "C"));

    private static final List<ScenarioLocation> LOCS_3 = List.of(
        new ScenarioLocation("loc1", "L1", null, null),
        new ScenarioLocation("loc2", "L2", null, null),
        new ScenarioLocation("loc3", "L3", null, null));

    private static final List<String> POOL_3 = List.of("loc1", "loc2", "loc3");

    private static final List<ScenarioItem> ITEMS_3 = List.of(
        new ScenarioItem("i1", "I1", "loc1"),
        new ScenarioItem("i2", "I2", "loc2"),
        new ScenarioItem("i3", "I3", "loc3"));

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
            "c", false, 2, null
        );
    }

    @Test
    void valid_scenario_passes() {
        assertThat(validator.validate(validScenario())).isEmpty();
    }

    @Test
    void unknown_pool_location_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3,
            List.of("loc1", "loc2", "unknown"), ITEMS_3,
            "c", false, 2, null
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void pool_smaller_than_characters_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30,
            CHARS_3,
            List.of(new ScenarioLocation("loc1", "L1", null, null), new ScenarioLocation("loc2", "L2", null, null)),
            List.of("loc1", "loc2"),
            List.of(new ScenarioItem("i1", "I1", "loc1"), new ScenarioItem("i2", "I2", "loc2")),
            "c", false, 2, null
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void location_without_origin_item_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3,
            List.of(new ScenarioItem("i1", "I1", "loc1"), new ScenarioItem("i3", "I3", "loc3")), // loc2 에 origin item 없음
            "c", false, 2, null
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void unknown_culprit_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3, ITEMS_3,
            "nobody", false, 2, null
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void rounds_count_mismatch_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3, ITEMS_3,
            "c", false, 3,
            List.of(new Round(null, null, 60), new Round("R2.", null, 60)) // only 2 rounds but round_count=3
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void rounds_k2_blank_prompt_fails() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3, ITEMS_3,
            "c", false, 2,
            List.of(new Round(null, null, 60), new Round("", null, 60)) // k=2 has blank prompt
        );
        assertThat(validator.validate(s)).isPresent();
    }

    @Test
    void rounds_null_passes_without_validation() {
        // rounds=null is optional — existing scenarios without rounds still load
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3, ITEMS_3,
            "c", false, 2, null
        );
        assertThat(validator.validate(s)).isEmpty();
    }

    @Test
    void rounds_valid_passes() {
        var s = new Scenario(
            "id", "title", "summary", null, 30, CHARS_3, LOCS_3, POOL_3, ITEMS_3,
            "c", false, 2,
            List.of(new Round(null, null, 60), new Round("두 번째 라운드.", null, 60))
        );
        assertThat(validator.validate(s)).isEmpty();
    }
}
