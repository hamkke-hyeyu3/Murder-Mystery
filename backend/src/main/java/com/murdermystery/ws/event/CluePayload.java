package com.murdermystery.ws.event;

public record CluePayload(
    String id,
    String itemId,
    String title,
    String originLocationId,
    int roundNumberDiscovered,
    long discoveredAt,
    String source
) {}
