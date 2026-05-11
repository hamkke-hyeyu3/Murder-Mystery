package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ClueAclRepository extends JpaRepository<ClueAcl, ClueAclId> {

    @Query("SELECT ca FROM ClueAcl ca JOIN Clue c ON ca.clueId = c.id WHERE c.sessionId = :sessionId AND ca.playerId = :playerId")
    List<ClueAcl> findBySessionIdAndPlayerId(UUID sessionId, UUID playerId);
}
