package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioCharacter(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("speech_style") String speechStyle,
    @JsonProperty("background") String background,
    @JsonProperty("motive") String motive,
    @JsonProperty("alibi") String alibi,
    @JsonProperty("secret") String secret,
    @JsonProperty("relationships") String relationships,
    @JsonProperty("alibi_location_id") String alibiLocationId,
    @JsonProperty("objectives_by_round") List<RoundObjective> objectivesByRound,
    @JsonProperty("missions") List<Mission> missions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mission(
        @JsonProperty("label") String label,
        @JsonProperty("description") String description
    ) {}
}
