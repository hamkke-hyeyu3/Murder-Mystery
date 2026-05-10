package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rounds")
@IdClass(RoundId.class)
public class RoundEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "round_number")
    private int roundNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "common_hint", columnDefinition = "text")
    private String commonHint;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected RoundEntity() {}

    public RoundEntity(UUID sessionId, int roundNumber, String prompt, String commonHint,
                       Instant startedAt, Instant deadlineAt) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.prompt = prompt;
        this.commonHint = commonHint;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
    }

    public UUID getSessionId() { return sessionId; }
    public int getRoundNumber() { return roundNumber; }
    public String getPrompt() { return prompt; }
    public String getCommonHint() { return commonHint; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
