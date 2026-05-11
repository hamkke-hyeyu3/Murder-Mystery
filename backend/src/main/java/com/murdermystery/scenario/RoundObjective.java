package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoundObjective(
    @JsonProperty("round") int round,
    @JsonProperty("text") String text
) {}
