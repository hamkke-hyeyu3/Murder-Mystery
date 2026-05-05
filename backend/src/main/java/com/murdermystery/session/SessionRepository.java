package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);
}
