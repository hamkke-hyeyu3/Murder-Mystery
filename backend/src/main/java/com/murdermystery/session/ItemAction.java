package com.murdermystery.session;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "item_actions")
public class ItemAction {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "action_type", length = 16, nullable = false)
    private String actionType;

    @Column(name = "actor_player_id", nullable = false)
    private UUID actorPlayerId;

    @Column(name = "target_player_id")
    private UUID targetPlayerId;

    @Column(name = "actor_clue_id")
    private UUID actorClueId;

    @Column(name = "target_clue_id")
    private UUID targetClueId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recipient_player_ids", columnDefinition = "uuid[]")
    private List<UUID> recipientPlayerIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ItemAction() {}

    public ItemAction(UUID sessionId, int roundNumber, String actionType, UUID actorPlayerId,
                      UUID targetPlayerId, UUID actorClueId, UUID targetClueId,
                      List<UUID> recipientPlayerIds, Instant createdAt) {
        this.sessionId = sessionId;
        this.roundNumber = roundNumber;
        this.actionType = actionType;
        this.actorPlayerId = actorPlayerId;
        this.targetPlayerId = targetPlayerId;
        this.actorClueId = actorClueId;
        this.targetClueId = targetClueId;
        this.recipientPlayerIds = recipientPlayerIds;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getRoundNumber() { return roundNumber; }
    public String getActionType() { return actionType; }
    public UUID getActorPlayerId() { return actorPlayerId; }
    public UUID getTargetPlayerId() { return targetPlayerId; }
    public UUID getActorClueId() { return actorClueId; }
    public UUID getTargetClueId() { return targetClueId; }
    public List<UUID> getRecipientPlayerIds() { return recipientPlayerIds; }
    public Instant getCreatedAt() { return createdAt; }
}
