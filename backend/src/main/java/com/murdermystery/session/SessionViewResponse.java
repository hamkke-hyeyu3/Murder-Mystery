package com.murdermystery.session;

import java.util.List;

public record SessionViewResponse(
    String sessionId,
    String inviteCode,
    String scenarioId,
    String phase,
    int requiredCharacterCount,
    int joinedCount,
    List<PlayerSummary> players
) {}
