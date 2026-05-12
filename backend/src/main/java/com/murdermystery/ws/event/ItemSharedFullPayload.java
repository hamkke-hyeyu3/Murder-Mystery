package com.murdermystery.ws.event;

public record ItemSharedFullPayload(
    String actorPlayerId,
    String actorNickname,
    String clueId,
    int roundNumber,
    String actionId,
    long occurredAt
) {}
