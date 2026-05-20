package com.murdermystery.ws.event;

import java.util.List;

public record VoteStartedPayload(
    int roundNo,
    long deadlineAt,
    List<String> candidateCharacterIds
) {}
