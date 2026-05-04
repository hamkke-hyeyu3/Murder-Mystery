package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioLocation(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("icon") String icon,
    @JsonProperty("description") String description
) {}
