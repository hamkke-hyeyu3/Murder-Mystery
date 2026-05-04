package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioItem(
    @JsonProperty("id") String id,
    @JsonProperty("title") String title,
    @JsonProperty("origin_location_id") String originLocationId
) {}
