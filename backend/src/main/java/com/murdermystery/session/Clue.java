package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clues")
public class Clue {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "round_number_discovered", nullable = false)
    private int roundNumberDiscovered;

    @Column(name = "item_id", length = 64, nullable = false)
    private String itemId;

    @Column(name = "origin_location_id", length = 64, nullable = false)
    private String originLocationId;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "discovered_by_player_id", nullable = false)
    private UUID discoveredByPlayerId;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "current_owner_player_id", nullable = false)
    private UUID currentOwnerPlayerId;

    protected Clue() {}

    public Clue(UUID sessionId, int roundNumberDiscovered, String itemId,
                String originLocationId, String title, UUID discoveredByPlayerId, Instant discoveredAt) {
        this.sessionId = sessionId;
        this.roundNumberDiscovered = roundNumberDiscovered;
        this.itemId = itemId;
        this.originLocationId = originLocationId;
        this.title = title;
        this.discoveredByPlayerId = discoveredByPlayerId;
        this.discoveredAt = discoveredAt;
        this.currentOwnerPlayerId = discoveredByPlayerId;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getRoundNumberDiscovered() { return roundNumberDiscovered; }
    public String getItemId() { return itemId; }
    public String getOriginLocationId() { return originLocationId; }
    public String getTitle() { return title; }
    public UUID getDiscoveredByPlayerId() { return discoveredByPlayerId; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public UUID getCurrentOwnerPlayerId() { return currentOwnerPlayerId; }
    public void setCurrentOwnerPlayerId(UUID currentOwnerPlayerId) { this.currentOwnerPlayerId = currentOwnerPlayerId; }
}
