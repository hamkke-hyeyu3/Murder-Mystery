package com.murdermystery.ws.event;

public record RoundStartedPayload(
    int roundNumber,
    String prompt,
    String commonHint,
    long deadlineAt,
    long startedAt
) {}
