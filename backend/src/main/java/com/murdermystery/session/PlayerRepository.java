package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {
    boolean existsBySessionIdAndNickname(UUID sessionId, String nickname);
}
