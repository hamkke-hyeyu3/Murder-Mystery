package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votes")
@IdClass(VoteId.class)
public class Vote {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "round_no")
    private int roundNo;

    @Id
    @Column(name = "voter_player_id")
    private UUID voterPlayerId;

    @Column(name = "target_character_id", nullable = false)
    private String targetCharacterId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    protected Vote() {}

    public Vote(UUID sessionId, int roundNo, UUID voterPlayerId, String targetCharacterId, Instant votedAt) {
        this.sessionId = sessionId;
        this.roundNo = roundNo;
        this.voterPlayerId = voterPlayerId;
        this.targetCharacterId = targetCharacterId;
        this.votedAt = votedAt;
    }

    public UUID getSessionId() { return sessionId; }
    public int getRoundNo() { return roundNo; }
    public UUID getVoterPlayerId() { return voterPlayerId; }
    public String getTargetCharacterId() { return targetCharacterId; }
    public Instant getVotedAt() { return votedAt; }

    public void setTargetCharacterId(String targetCharacterId) { this.targetCharacterId = targetCharacterId; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }
}
