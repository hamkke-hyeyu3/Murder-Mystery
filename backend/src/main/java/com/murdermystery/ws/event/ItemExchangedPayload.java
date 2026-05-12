package com.murdermystery.ws.event;

public record ItemExchangedPayload(
    String actorPlayerId,
    String actorNickname,
    String partnerPlayerId,
    String partnerNickname,
    int roundNumber,
    String actorClueId,
    String partnerClueId,
    String actionId,
    long occurredAt
) {}
