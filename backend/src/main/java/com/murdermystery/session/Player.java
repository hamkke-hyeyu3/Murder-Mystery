package com.murdermystery.session;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "players",
    uniqueConstraints = @UniqueConstraint(
        name = "players_nickname_unique",
        columnNames = {"session_id", "nickname"}
    )
)
public class Player implements Persistable<UUID> {

    @Id
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(length = 20, nullable = false)
    private String nickname;

    @Column(name = "is_host", nullable = false)
    private boolean isHost;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected Player() {}

    public Player(String nickname, boolean isHost) {
        this.nickname = nickname;
        this.isHost = isHost;
        this.joinedAt = Instant.now();
    }

    @Override
    public boolean isNew() { return isNew; }

    @PostPersist
    @PostLoad
    void markNotNew() { isNew = false; }

    void setSession(Session session) { this.session = session; }

    @Override
    public UUID getId() { return id; }
    public String getNickname() { return nickname; }
    public boolean isHost() { return isHost; }
    public Instant getJoinedAt() { return joinedAt; }
    public Session getSession() { return session; }
}
