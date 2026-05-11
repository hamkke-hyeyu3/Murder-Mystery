package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clue_acl")
@IdClass(ClueAclId.class)
public class ClueAcl {

    @Id
    @Column(name = "clue_id")
    private UUID clueId;

    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    // 'discovery' (T-09), 'exchange'|'share_all'|'share_partial' (T-11에서 추가)
    @Column(length = 16, nullable = false)
    private String source;

    protected ClueAcl() {}

    public ClueAcl(UUID clueId, UUID playerId, Instant grantedAt, String source) {
        this.clueId = clueId;
        this.playerId = playerId;
        this.grantedAt = grantedAt;
        this.source = source;
    }

    public UUID getClueId() { return clueId; }
    public UUID getPlayerId() { return playerId; }
    public Instant getGrantedAt() { return grantedAt; }
    public String getSource() { return source; }
}
