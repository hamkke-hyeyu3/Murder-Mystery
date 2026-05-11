package com.murdermystery.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class LocationOccupancyId implements Serializable {

    private UUID sessionId;
    private int roundNumber;
    private String locationId;

    public LocationOccupancyId() {}

    public LocationOccupancyId(UUID sessionId, int roundNumber, String locationId) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.locationId = locationId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocationOccupancyId other)) return false;
        return roundNumber == other.roundNumber
                && Objects.equals(sessionId, other.sessionId)
                && Objects.equals(locationId, other.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, roundNumber, locationId);
    }
}
