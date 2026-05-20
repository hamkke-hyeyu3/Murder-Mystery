package com.murdermystery.ws.event;

public record CulpritRevealStartedPayload(
    String outcome,
    String culpritCharacterId,
    String accusedCharacterId
) {}
