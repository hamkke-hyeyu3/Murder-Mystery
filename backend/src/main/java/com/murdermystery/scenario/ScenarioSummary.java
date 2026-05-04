package com.murdermystery.scenario;

public record ScenarioSummary(
    String id,
    String title,
    String icon,
    String summary,
    int playerCount,
    int estimatedMinutes
) {}
