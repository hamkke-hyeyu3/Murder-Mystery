package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PrivateTalkRepository extends JpaRepository<PrivateTalk, UUID> {

    @Query("SELECT p FROM PrivateTalk p WHERE p.sessionId = :sid AND p.endReason IS NULL")
    Optional<PrivateTalk> findActiveBySessionId(@Param("sid") UUID sessionId);
}
