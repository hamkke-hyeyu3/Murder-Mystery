package com.murdermystery.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class SurveyResponseId implements Serializable {

    private UUID sessionId;
    private UUID playerId;

    public SurveyResponseId() {}

    public SurveyResponseId(UUID sessionId, UUID playerId) {
        this.sessionId = sessionId;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SurveyResponseId other)) return false;
        return Objects.equals(sessionId, other.sessionId) && Objects.equals(playerId, other.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, playerId);
    }
}
