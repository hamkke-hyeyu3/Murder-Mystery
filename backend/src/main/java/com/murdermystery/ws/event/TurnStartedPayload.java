package com.murdermystery.ws.event;

import java.util.List;

public record TurnStartedPayload(
    int roundNumber,
    int turnIndex,
    String playerId,
    String characterId,
    long deadlineAt,
    List<String> candidateLocationIds
) {}
