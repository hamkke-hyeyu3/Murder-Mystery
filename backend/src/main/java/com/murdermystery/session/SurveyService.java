package com.murdermystery.session;

import com.murdermystery.ws.SurveySubmitRequest;
import com.murdermystery.ws.event.SessionEndedPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import com.murdermystery.ws.event.SurveyResponseRecordedPayload;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SurveyService {

    private static final Logger log = LoggerFactory.getLogger(SurveyService.class);

    private final SessionRepository sessionRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final EndingService endingService;

    private final ConcurrentHashMap<String, Boolean> endedSessions = new ConcurrentHashMap<>();

    public SurveyService(
        SessionRepository sessionRepository,
        SurveyResponseRepository surveyResponseRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        EndingService endingService
    ) {
        this.sessionRepository = sessionRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.endingService = endingService;
    }

    @PostConstruct
    void registerTimeoutCallback() {
        endingService.setSurveyEndCallback(this::endSession);
    }

    public void submit(UUID sessionId, UUID playerId, SurveySubmitRequest request) {
        validateScores(request);

        SubmitResult result = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

            if (!"in_progress".equals(session.getPhase()) || !"survey".equals(session.getState())) {
                throw new SurveyPhaseRequiredException();
            }

            boolean playerInSession = session.getPlayers().stream()
                .anyMatch(p -> playerId.equals(p.getId()));
            if (!playerInSession) {
                throw new PlayerNotInSessionException();
            }

            SurveyResponseId id = new SurveyResponseId(sessionId, playerId);
            SurveyResponse existing = surveyResponseRepository.findById(id).orElse(null);

            boolean isNew = existing == null;
            if (isNew) {
                surveyResponseRepository.save(
                    new SurveyResponse(sessionId, playerId,
                        request.platformScore(), request.workScore(), truncate(request.freeText()),
                        Instant.now())
                );
            } else {
                existing.update(
                    request.platformScore(), request.workScore(), truncate(request.freeText()),
                    Instant.now()
                );
                surveyResponseRepository.save(existing);
            }

            long respondedCount = surveyResponseRepository.countBySessionId(sessionId);
            int totalCount = session.getPlayers().size();

            return new SubmitResult(playerId.toString(), (int) respondedCount, totalCount, respondedCount >= totalCount, isNew);
        });

        if (!result.isNew()) {
            log.debug("survey submit idempotent: player {} already responded in session {}", playerId, sessionId);
            return;
        }

        eventPublisher.publish(sessionId.toString(), "SURVEY_RESPONSE_RECORDED",
            new SurveyResponseRecordedPayload(result.playerId(), result.respondedCount(), result.totalCount()));

        if (result.allDone()) {
            endSession(sessionId.toString());
        }
    }

    public void endSession(String sessionId) {
        if (endedSessions.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            log.debug("endSession already called for session {} — skipping", sessionId);
            return;
        }

        boolean transitioned = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            UUID uuid = UUID.fromString(sessionId);
            Session session = sessionRepository.findByIdForUpdate(uuid).orElse(null);
            if (session == null) return false;
            if ("ended".equals(session.getState())) {
                log.debug("endSession stale: session {} already in state 'ended'", sessionId);
                return false;
            }
            session.setState("ended");
            session.setPhase("ended");
            sessionRepository.saveAndFlush(session);
            return true;
        }));

        if (!transitioned) return;

        eventPublisher.publish(sessionId, "SESSION_STATE_CHANGED", new SessionStateChangedPayload("ended", null));
        eventPublisher.publish(sessionId, "SESSION_ENDED", new SessionEndedPayload());
    }

    private void validateScores(SurveySubmitRequest request) {
        if (request.platformScore() != null && (request.platformScore() < 1 || request.platformScore() > 5)) {
            throw new IllegalArgumentException("platformScore out of range: " + request.platformScore());
        }
        if (request.workScore() != null && (request.workScore() < 1 || request.workScore() > 5)) {
            throw new IllegalArgumentException("workScore out of range: " + request.workScore());
        }
        if (request.freeText() != null && request.freeText().length() > 80) {
            throw new IllegalArgumentException("freeText exceeds 80 chars");
        }
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > 80 ? text.substring(0, 80) : text;
    }

    @PreDestroy
    void clearGuard() {
        endedSessions.clear();
    }

    /** Package-private test hook. */
    void clearEndedSessionsForTest() {
        endedSessions.clear();
    }

    private record SubmitResult(String playerId, int respondedCount, int totalCount, boolean allDone, boolean isNew) {}
}
