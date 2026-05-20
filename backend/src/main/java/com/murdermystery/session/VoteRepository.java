package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, VoteId> {

    List<Vote> findBySessionIdAndRoundNo(UUID sessionId, int roundNo);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.sessionId = :sessionId AND v.roundNo = :roundNo")
    long countBySessionIdAndRoundNo(@Param("sessionId") UUID sessionId, @Param("roundNo") int roundNo);
}
