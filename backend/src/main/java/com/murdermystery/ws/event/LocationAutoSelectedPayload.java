package com.murdermystery.ws.event;

public record LocationAutoSelectedPayload(
    int roundNumber,
    int turnIndex,
    String playerId,
    String characterId,
    String locationId
) {}
