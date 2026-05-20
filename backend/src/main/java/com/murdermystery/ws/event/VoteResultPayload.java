package com.murdermystery.ws.event;

import java.util.List;

public record VoteResultPayload(
    int roundNo,
    String outcome,
    String winnerCharacterId,
    List<String> tiedCharacterIds,
    List<TallyEntry> tally
) {
    public record TallyEntry(String characterId, int count) {}
}
