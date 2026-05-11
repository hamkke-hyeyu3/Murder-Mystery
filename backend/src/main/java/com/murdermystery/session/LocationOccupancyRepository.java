package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationOccupancyRepository extends JpaRepository<LocationOccupancy, LocationOccupancyId> {

    List<LocationOccupancy> findBySessionIdAndRoundNumber(UUID sessionId, int roundNumber);

    Optional<LocationOccupancy> findBySessionIdAndRoundNumberAndPlayerId(UUID sessionId, int roundNumber, UUID playerId);

    boolean existsBySessionIdAndRoundNumberAndPlayerId(UUID sessionId, int roundNumber, UUID playerId);
}
