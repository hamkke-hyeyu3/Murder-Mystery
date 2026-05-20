package com.murdermystery.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class VoteId implements Serializable {

    private UUID sessionId;
    private int roundNo;
    private UUID voterPlayerId;

    public VoteId() {}

    public VoteId(UUID sessionId, int roundNo, UUID voterPlayerId) {
        this.sessionId = sessionId;
        this.roundNo = roundNo;
        this.voterPlayerId = voterPlayerId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VoteId other)) return false;
        return roundNo == other.roundNo
            && Objects.equals(sessionId, other.sessionId)
            && Objects.equals(voterPlayerId, other.voterPlayerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, roundNo, voterPlayerId);
    }
}
