package com.murdermystery.ws.event;

import java.util.List;

public record ItemSharedPartialPayload(
    String actorPlayerId,
    String actorNickname,
    String clueId,
    int roundNumber,
    List<RecipientView> recipients,
    String actionId,
    long occurredAt
) {
    public record RecipientView(String playerId, String nickname) {}
}
