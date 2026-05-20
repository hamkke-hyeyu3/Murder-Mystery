package com.murdermystery.ws.event;

import java.util.List;

public record RunoffStartedPayload(
    int roundNo,
    long deadlineAt,
    List<String> candidateCharacterIds,
    List<String> tiedFromPreviousRound
) {}
