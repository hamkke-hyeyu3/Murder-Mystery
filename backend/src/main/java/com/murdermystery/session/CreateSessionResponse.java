package com.murdermystery.session;

public record CreateSessionResponse(
    String sessionId,
    String inviteCode,
    String scenarioId,
    String hostNickname,
    String phase,
    String playerId
) {}
