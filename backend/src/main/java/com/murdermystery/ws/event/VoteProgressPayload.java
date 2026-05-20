package com.murdermystery.ws.event;

public record VoteProgressPayload(
    int roundNo,
    int submittedCount,
    int totalCount
) {}
