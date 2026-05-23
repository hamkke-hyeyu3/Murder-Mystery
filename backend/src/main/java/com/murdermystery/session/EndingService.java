package com.murdermystery.session;

import com.murdermystery.ws.event.DebriefStartedPayload;
import com.murdermystery.ws.event.EndingStartedPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import com.murdermystery.ws.event.SurveyAvailablePayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Drives the post-game chain: ending → debrief → survey.
 * Called by MissionEndingHelper after the session state is persisted as 'ending'.
 */
@Service
public class EndingService {

    private static final Logger log = LoggerFactory.getLogger(EndingService.class);

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;

    @Value("${app.ending.debrief-delay-ms:10000}")
    long debriefDelayMs;

    @Value("${app.ending.survey-delay-ms:30000}")
    long surveyDelayMs;

    @Value("${app.session.survey-timeout-ms:60000}")
    long surveyTimeoutMs;

    // JVM-memory once-only guard
    private final ConcurrentHashMap<String, Boolean> startedEndings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDebrief = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSurvey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSessionEnd = new ConcurrentHashMap<>();

    private Consumer<String> surveyEndCallback = sessionId -> {};

    public EndingService(
        SessionRepository sessionRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService gameScheduler
    ) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.scheduler = gameScheduler;
    }

    /** Injected by SurveyService at startup to avoid circular dependency. */
    public void setSurveyEndCallback(Consumer<String> callback) {
        this.surveyEndCallback = callback;
    }

    public void startEnding(String sessionId) {
        if (startedEndings.putIfAbsent(sessionId, Boolean.TRUE) != null) {
            log.debug("startEnding already running for session {} — skipping", sessionId);
            return;
        }

        eventPublisher.publish(sessionId, "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("ending", null));
        eventPublisher.publish(sessionId, "ENDING_STARTED", new EndingStartedPayload());

        ScheduledFuture<?> debriefFuture = scheduler.schedule(
            () -> {
                try {
                    transitionToDebrief(sessionId);
                } catch (Exception e) {
                    log.error("transitionToDebrief failed for session {}", sessionId, e);
                }
            },
            debriefDelayMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingDebrief.put(sessionId, debriefFuture);
        if (old != null) old.cancel(false);
    }

    private void transitionToDebrief(String sessionId) {
        boolean transitioned = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            UUID uuid = UUID.fromString(sessionId);
            Session session = sessionRepository.findByIdForUpdate(uuid).orElse(null);
            if (session == null) return false;
            if (!"ending".equals(session.getState())) {
                log.debug("transitionToDebrief stale: session {} state is '{}', expected 'ending'", sessionId, session.getState());
                return false;
            }
            session.setState("debrief");
            sessionRepository.saveAndFlush(session);
            return true;
        }));

        if (!transitioned) return;

        eventPublisher.publish(sessionId, "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("debrief", null));
        eventPublisher.publish(sessionId, "DEBRIEF_STARTED", new DebriefStartedPayload());

        ScheduledFuture<?> surveyFuture = scheduler.schedule(
            () -> {
                try {
                    transitionToSurvey(sessionId);
                } catch (Exception e) {
                    log.error("transitionToSurvey failed for session {}", sessionId, e);
                }
            },
            surveyDelayMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingSurvey.put(sessionId, surveyFuture);
        if (old != null) old.cancel(false);
    }

    private void transitionToSurvey(String sessionId) {
        boolean transitioned = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            UUID uuid = UUID.fromString(sessionId);
            Session session = sessionRepository.findByIdForUpdate(uuid).orElse(null);
            if (session == null) return false;
            if (!"debrief".equals(session.getState())) {
                log.debug("transitionToSurvey stale: session {} state is '{}', expected 'debrief'", sessionId, session.getState());
                return false;
            }
            session.setState("survey");
            sessionRepository.saveAndFlush(session);
            return true;
        }));

        if (!transitioned) return;

        eventPublisher.publish(sessionId, "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("survey", null));
        eventPublisher.publish(sessionId, "SURVEY_AVAILABLE", new SurveyAvailablePayload());

        ScheduledFuture<?> endFuture = scheduler.schedule(
            () -> {
                try {
                    surveyEndCallback.accept(sessionId);
                } catch (Exception e) {
                    log.error("surveyTimeout endSession failed for session {}", sessionId, e);
                }
            },
            surveyTimeoutMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingSessionEnd.put(sessionId, endFuture);
        if (old != null) old.cancel(false);
    }

    @PreDestroy
    void shutdown() {
        pendingDebrief.forEach((k, f) -> f.cancel(false));
        pendingDebrief.clear();
        pendingSurvey.forEach((k, f) -> f.cancel(false));
        pendingSurvey.clear();
        pendingSessionEnd.forEach((k, f) -> f.cancel(false));
        pendingSessionEnd.clear();
        startedEndings.clear();
    }

    /** Package-private test hook. */
    void cancelPendingForTest() {
        pendingDebrief.forEach((k, f) -> f.cancel(false));
        pendingDebrief.clear();
        pendingSurvey.forEach((k, f) -> f.cancel(false));
        pendingSurvey.clear();
        pendingSessionEnd.forEach((k, f) -> f.cancel(false));
        pendingSessionEnd.clear();
        startedEndings.clear();
    }
}
