package com.murdermystery.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, SurveyResponseId> {

    long countBySessionId(UUID sessionId);
}
