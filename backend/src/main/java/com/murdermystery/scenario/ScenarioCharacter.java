package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioCharacter(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name
) {}
