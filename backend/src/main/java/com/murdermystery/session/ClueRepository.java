package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClueRepository extends JpaRepository<Clue, UUID> {

    List<Clue> findBySessionId(UUID sessionId);

    List<Clue> findBySessionIdAndDiscoveredByPlayerId(UUID sessionId, UUID playerId);

    @Query("SELECT c.id AS id, c.itemId AS itemId, c.title AS title, c.currentOwnerPlayerId AS currentOwnerPlayerId, c.roundNumberDiscovered AS roundNumberDiscovered FROM Clue c WHERE c.sessionId = :sid")
    List<OwnedClueProjection> findOwnedClueProjectionsBySessionId(@Param("sid") UUID sessionId);

    interface OwnedClueProjection {
        UUID getId();
        String getItemId();
        String getTitle();
        UUID getCurrentOwnerPlayerId();
        int getRoundNumberDiscovered();
    }
}
