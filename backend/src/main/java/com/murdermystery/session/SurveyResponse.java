package com.murdermystery.session;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "survey_responses")
@IdClass(SurveyResponseId.class)
public class SurveyResponse {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "player_id")
    private UUID playerId;

    @Column(name = "platform_score")
    private Integer platformScore;

    @Column(name = "work_score")
    private Integer workScore;

    @Column(name = "free_text", length = 80)
    private String freeText;

    @Column(name = "responded_at", nullable = false)
    private Instant respondedAt;

    protected SurveyResponse() {}

    public SurveyResponse(UUID sessionId, UUID playerId, Integer platformScore, Integer workScore, String freeText, Instant respondedAt) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.platformScore = platformScore;
        this.workScore = workScore;
        this.freeText = freeText;
        this.respondedAt = respondedAt;
    }

    public UUID getSessionId() { return sessionId; }
    public UUID getPlayerId() { return playerId; }
    public Integer getPlatformScore() { return platformScore; }
    public Integer getWorkScore() { return workScore; }
    public String getFreeText() { return freeText; }
    public Instant getRespondedAt() { return respondedAt; }

    public void update(Integer platformScore, Integer workScore, String freeText, Instant respondedAt) {
        this.platformScore = platformScore;
        this.workScore = workScore;
        this.freeText = freeText;
        this.respondedAt = respondedAt;
    }
}
