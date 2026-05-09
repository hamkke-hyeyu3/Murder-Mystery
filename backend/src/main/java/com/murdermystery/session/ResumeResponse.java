package com.murdermystery.session;

import java.util.List;

public record ResumeResponse(
    String sessionId,
    String inviteCode,
    String scenarioId,
    String phase,
    String nickname,
    String playerId,
    boolean isHost,
    List<PlayerSummary> players
) {}
