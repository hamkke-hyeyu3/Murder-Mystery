package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClueRepository extends JpaRepository<Clue, UUID> {

    List<Clue> findBySessionId(UUID sessionId);

    List<Clue> findBySessionIdAndDiscoveredByPlayerId(UUID sessionId, UUID playerId);
}
