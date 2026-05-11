package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_occupancy")
@IdClass(LocationOccupancyId.class)
public class LocationOccupancy {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "round_number")
    private int roundNumber;

    @Id
    @Column(name = "location_id", length = 64)
    private String locationId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "character_id", length = 64, nullable = false)
    private String characterId;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    @Column(name = "is_auto_selected", nullable = false)
    private boolean isAutoSelected;

    protected LocationOccupancy() {}

    public LocationOccupancy(UUID sessionId, int roundNumber, String locationId,
                             UUID playerId, String characterId, Instant selectedAt, boolean isAutoSelected) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.locationId = locationId;
        this.playerId = playerId;
        this.characterId = characterId;
        this.selectedAt = selectedAt;
        this.isAutoSelected = isAutoSelected;
    }

    public UUID getSessionId() { return sessionId; }
    public int getRoundNumber() { return roundNumber; }
    public String getLocationId() { return locationId; }
    public UUID getPlayerId() { return playerId; }
    public String getCharacterId() { return characterId; }
    public Instant getSelectedAt() { return selectedAt; }
    public boolean isAutoSelected() { return isAutoSelected; }
}
