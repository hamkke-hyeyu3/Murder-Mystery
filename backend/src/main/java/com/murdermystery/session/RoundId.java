package com.murdermystery.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RoundId implements Serializable {

    private UUID sessionId;
    private int roundNumber;

    public RoundId() {}

    public RoundId(UUID sessionId, int roundNumber) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RoundId other)) return false;
        return roundNumber == other.roundNumber && Objects.equals(sessionId, other.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, roundNumber);
    }
}
