package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "private_talks")
public class PrivateTalk {

    @Id
    private UUID id = UUID.randomUUID();

    @Version
    private long version;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "requester_player_id", nullable = false)
    private UUID requesterPlayerId;

    @Column(name = "target_player_id", nullable = false)
    private UUID targetPlayerId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason")
    private String endReason;

    protected PrivateTalk() {}

    public PrivateTalk(UUID sessionId, int roundNumber, UUID requesterPlayerId,
                       UUID targetPlayerId, Instant requestedAt) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.requesterPlayerId = requesterPlayerId;
        this.targetPlayerId = targetPlayerId;
        this.requestedAt = requestedAt;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getRoundNumber() { return roundNumber; }
    public UUID getRequesterPlayerId() { return requesterPlayerId; }
    public UUID getTargetPlayerId() { return targetPlayerId; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }
    public long getVersion() { return version; }

    public void markStarted(Instant now) {
        this.startedAt = now;
    }

    public void markEnded(String reason, Instant now) {
        this.endReason = reason;
        this.endedAt = now;
    }

    public boolean isActive() {
        return endReason == null;
    }

    public boolean isStarted() {
        return startedAt != null;
    }
}
