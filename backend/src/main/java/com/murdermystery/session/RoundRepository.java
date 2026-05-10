package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoundRepository extends JpaRepository<RoundEntity, RoundId> {
    Optional<RoundEntity> findBySessionIdAndRoundNumber(UUID sessionId, int roundNumber);
}
