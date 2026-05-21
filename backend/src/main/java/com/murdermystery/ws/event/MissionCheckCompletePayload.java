package com.murdermystery.ws.event;

public record MissionCheckCompletePayload(
    String playerId,
    String nickname,
    int checkedCount,
    int totalCount
) {}
