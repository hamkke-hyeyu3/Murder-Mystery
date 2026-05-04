package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(
    @JsonProperty("id") String id,
    @JsonProperty("title") String title,
    @JsonProperty("summary") String summary,
    @JsonProperty("icon") String icon,
    @JsonProperty("estimated_minutes") int estimatedMinutes,
    @JsonProperty("characters") List<ScenarioCharacter> characters,
    @JsonProperty("locations") List<ScenarioLocation> locations,
    @JsonProperty("location_pool") List<String> locationPool,
    @JsonProperty("items") List<ScenarioItem> items,
    @JsonProperty("true_culprit_character_id") String trueCulpritCharacterId,
    @JsonProperty("allow_private_talk") boolean allowPrivateTalk,
    @JsonProperty("round_count") int roundCount
) {}
