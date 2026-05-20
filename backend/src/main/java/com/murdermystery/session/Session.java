package com.murdermystery.session;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class Session implements Persistable<UUID> {

    @Id
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @Column(name = "invite_code", length = 6, nullable = false, unique = true)
    private String inviteCode;

    @Column(name = "scenario_id", length = 64, nullable = false)
    private String scenarioId;

    @Version
    private long version;

    @Column(length = 16, nullable = false)
    private String phase = "lobby";

    @Column(length = 32)
    private String state;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "turn_order", columnDefinition = "text[]")
    private List<String> turnOrder;

    @Column(name = "current_round_number")
    private Integer currentRoundNumber;

    @Column(name = "vote_round_no")
    private Integer voteRoundNo;

    @Column(name = "vote_outcome", length = 16)
    private String voteOutcome;

    @Column(name = "vote_winner_character_id")
    private String voteWinnerCharacterId;

    @Column(name = "vote_deadline_at")
    private Instant voteDeadlineAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Player> players = new ArrayList<>();

    protected Session() {}

    public Session(String inviteCode, String scenarioId) {
        this.inviteCode = inviteCode;
        this.scenarioId = scenarioId;
    }

    public void addPlayer(Player player) {
        players.add(player);
        player.setSession(this);
    }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { isNew = false; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void removePlayer(Player player) {
        players.remove(player);
        player.setSession(null);
    }

    @Override
    public UUID getId() { return id; }
    public String getInviteCode() { return inviteCode; }
    public String getScenarioId() { return scenarioId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public List<String> getTurnOrder() { return turnOrder; }
    public void setTurnOrder(List<String> turnOrder) { this.turnOrder = turnOrder; }
    public Integer getCurrentRoundNumber() { return currentRoundNumber; }
    public void setCurrentRoundNumber(Integer currentRoundNumber) { this.currentRoundNumber = currentRoundNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<Player> getPlayers() { return players; }
    public Integer getVoteRoundNo() { return voteRoundNo; }
    public void setVoteRoundNo(Integer voteRoundNo) { this.voteRoundNo = voteRoundNo; }
    public String getVoteOutcome() { return voteOutcome; }
    public void setVoteOutcome(String voteOutcome) { this.voteOutcome = voteOutcome; }
    public String getVoteWinnerCharacterId() { return voteWinnerCharacterId; }
    public void setVoteWinnerCharacterId(String voteWinnerCharacterId) { this.voteWinnerCharacterId = voteWinnerCharacterId; }
    public Instant getVoteDeadlineAt() { return voteDeadlineAt; }
    public void setVoteDeadlineAt(Instant voteDeadlineAt) { this.voteDeadlineAt = voteDeadlineAt; }
}
