package com.murdermystery.ws.event;

import java.util.List;

public record CharacterCardDealtPayload(
    String characterId,
    String name,
    int turnOrderIndex,
    String speechStyle,
    String background,
    String motive,
    String alibi,
    String secret,
    String relationships,
    LocationRef alibiLocation,
    List<ItemRef> items
) {
    public record LocationRef(String id, String name, String icon) {}
    public record ItemRef(String id, String title, LocationRef originLocation) {}
}
