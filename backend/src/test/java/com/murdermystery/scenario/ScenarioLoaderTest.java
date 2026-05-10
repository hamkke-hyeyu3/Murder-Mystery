package com.murdermystery.scenario;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioLoaderTest {

    @Test
    void test_fixtures_load_one_valid_out_of_six() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios-fixtures/*.json");
        List<Scenario> loaded = loader.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().id()).isEqualTo("tiny-pass");
    }

    @Test
    void main_classpath_loads_only_toy_manor() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios/*.json");
        List<Scenario> loaded = loader.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().id()).isEqualTo("toy-manor");
    }

    @Test
    void toy_manor_fields_deserialize_correctly() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios/*.json");
        Scenario toyManor = loader.loadAll().getFirst();

        assertThat(toyManor.title()).isEqualTo("Toy Manor 살인 사건");
        assertThat(toyManor.characters()).hasSize(3);
        assertThat(toyManor.locationPool()).hasSize(4);
        assertThat(toyManor.roundCount()).isEqualTo(3);
        assertThat(toyManor.allowPrivateTalk()).isTrue();
        assertThat(toyManor.trueCulpritCharacterId()).isEqualTo("bob");
        assertThat(toyManor.rounds()).hasSize(3);
    }

    @Test
    void toy_manor_round1_has_no_prompt_for_platform_fallback() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios/*.json");
        Scenario toyManor = loader.loadAll().getFirst();

        // Round 1 omits prompt so RoundService applies DEFAULT_K1_INTRO_PROMPT fallback
        assertThat(toyManor.rounds().get(0).prompt()).isNullOrEmpty();
        assertThat(toyManor.rounds().get(0).timeLimitSec()).isGreaterThan(0);
    }

    @Test
    void toy_manor_round2_has_common_hint() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios/*.json");
        Scenario toyManor = loader.loadAll().getFirst();

        assertThat(toyManor.rounds().get(1).commonHint()).isNotBlank();
        assertThat(toyManor.rounds().get(1).prompt()).isNotBlank();
    }

    @Test
    void dev_duo_rounds_deserialize() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios-dev/*.json", "scenario-schema-dev.json");
        Scenario devDuo = loader.loadAll().getFirst();

        assertThat(devDuo.rounds()).hasSize(2);
        assertThat(devDuo.rounds().get(1).prompt()).isNotBlank();
    }

    @Test
    void dev_classpath_loads_dev_duo() throws IOException {
        var loader = new ScenarioLoader("classpath:scenarios-dev/*.json", "scenario-schema-dev.json");
        List<Scenario> loaded = loader.loadAll();

        assertThat(loaded).hasSize(1);
        Scenario devDuo = loaded.getFirst();
        assertThat(devDuo.id()).isEqualTo("dev-duo");
        assertThat(devDuo.characters()).hasSize(2);
        assertThat(devDuo.locationPool()).hasSize(2);
        assertThat(devDuo.trueCulpritCharacterId()).isEqualTo("guest");
    }
}
