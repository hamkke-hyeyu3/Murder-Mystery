package com.murdermystery.ws.event;

import java.util.List;

public record VoteStartedPayload(
    int roundNo,
    long deadlineAt,
    List<Candidate> candidates
) {
    public record Candidate(
        String characterId,
        String name,
        String playerNickname,
        String playerId
    ) {}
}
