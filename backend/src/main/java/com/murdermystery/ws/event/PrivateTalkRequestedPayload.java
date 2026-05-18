package com.murdermystery.ws.event;

public record PrivateTalkRequestedPayload(
    String requestId,
    String requesterPlayerId,
    String requesterNickname,
    String targetPlayerId,
    String targetNickname,
    long requestedAt,
    long expiresAt
) {}
