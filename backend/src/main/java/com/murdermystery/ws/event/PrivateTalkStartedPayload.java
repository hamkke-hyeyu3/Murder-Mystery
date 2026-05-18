package com.murdermystery.ws.event;

import java.util.List;

public record PrivateTalkStartedPayload(
    String requestId,
    List<Participant> participants,
    long startedAt
) {
    public record Participant(String playerId, String nickname) {}
}
