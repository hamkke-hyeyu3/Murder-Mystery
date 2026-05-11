package com.murdermystery.ws.event;

public record LocationSelectedPayload(
    int roundNumber,
    int turnIndex,
    String playerId,
    String characterId,
    String locationId
) {}
