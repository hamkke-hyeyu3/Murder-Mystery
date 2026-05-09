package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    boolean existsBySessionIdAndNickname(UUID sessionId, String nickname);
    boolean existsByIdAndNicknameAndSession_InviteCode(UUID id, String nickname, String inviteCode);
    Optional<Player> findBySessionIdAndDeviceId(UUID sessionId, UUID deviceId);
    Optional<Player> findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(UUID deviceId, String phase);
}
