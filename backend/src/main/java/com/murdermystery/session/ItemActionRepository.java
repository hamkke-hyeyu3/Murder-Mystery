package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ItemActionRepository extends JpaRepository<ItemAction, UUID> {
    boolean existsBySessionIdAndRoundNumberAndActionTypeAndActorPlayerIdAndActorClueId(
        UUID sessionId, int roundNumber, String actionType, UUID actorPlayerId, UUID actorClueId);
}
