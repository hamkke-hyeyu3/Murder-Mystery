package com.murdermystery.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Round(
    @JsonProperty("prompt") String prompt,
    @JsonProperty("common_hint") String commonHint,
    @JsonProperty("time_limit_sec") int timeLimitSec
) {}
