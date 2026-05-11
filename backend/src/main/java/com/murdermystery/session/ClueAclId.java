package com.murdermystery.session;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ClueAclId implements Serializable {

    private UUID clueId;
    private UUID playerId;

    public ClueAclId() {}

    public ClueAclId(UUID clueId, UUID playerId) {
        this.clueId = clueId;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClueAclId other)) return false;
        return Objects.equals(clueId, other.clueId) && Objects.equals(playerId, other.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clueId, playerId);
    }
}
