package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ItemActionRepository extends JpaRepository<ItemAction, UUID> {

    @Query("SELECT COUNT(a) > 0 FROM ItemAction a WHERE a.sessionId = :sid AND a.roundNumber = :rn AND a.actionType = 'share_all' AND a.actorClueId = :clueId")
    boolean existsShareAll(@Param("sid") UUID sessionId, @Param("rn") int roundNumber, @Param("clueId") UUID actorClueId);

    @Query("SELECT COUNT(a) > 0 FROM ItemAction a WHERE a.sessionId = :sid AND a.roundNumber = :rn AND a.actionType = 'exchange' AND ((a.actorClueId = :a AND a.targetClueId = :b) OR (a.actorClueId = :b AND a.targetClueId = :a))")
    boolean existsExchange(@Param("sid") UUID sessionId, @Param("rn") int roundNumber, @Param("a") UUID clueA, @Param("b") UUID clueB);
}
