package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    boolean existsBySessionIdAndNickname(UUID sessionId, String nickname);
    boolean existsByIdAndNicknameAndSession_InviteCode(UUID id, String nickname, String inviteCode);
    Optional<Player> findBySessionIdAndDeviceId(UUID sessionId, UUID deviceId);
    Optional<Player> findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(UUID deviceId, String phase);

    @Modifying
    @Transactional
    @Query("UPDATE Player p SET p.lastSeenAt = :at WHERE p.id = :playerId")
    int touchLastSeen(@Param("playerId") UUID playerId, @Param("at") Instant at);
}
