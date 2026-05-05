package com.murdermystery.session;

import java.util.List;

public record JoinResponse(
    String sessionId,
    String inviteCode,
    String scenarioId,
    String phase,
    String nickname,
    String playerId,
    List<PlayerSummary> players
) {}
