package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.RoundEndedPayload;
import com.murdermystery.ws.event.RoundTurnsCompletePayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RoundLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(RoundLifecycleService.class);

    private final SessionRepository sessionRepository;
    private final RoundRepository roundRepository;
    private final ScenarioRepository scenarioRepository;
    private final SessionEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;
    // ObjectProvider breaks the construction-time cycle: RoundService → RoundLifecycleService
    private final ObjectProvider<RoundService> roundServiceProvider;

    // session:roundNumber → pending time_limit future (cancelled if turns finish first)
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDeadlines = new ConcurrentHashMap<>();
    // session:roundNumber → sentinel ensuring endRound runs exactly once
    private final ConcurrentHashMap<String, Boolean> endedRounds = new ConcurrentHashMap<>();

    public RoundLifecycleService(
        SessionRepository sessionRepository,
        RoundRepository roundRepository,
        ScenarioRepository scenarioRepository,
        SessionEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate,
        ScheduledExecutorService gameScheduler,
        Clock systemClock,
        ObjectProvider<RoundService> roundServiceProvider
    ) {
        this.sessionRepository = sessionRepository;
        this.roundRepository = roundRepository;
        this.scenarioRepository = scenarioRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = gameScheduler;
        this.clock = systemClock;
        this.roundServiceProvider = roundServiceProvider;
    }

    /** Called by RoundService.startRound after persisting the round entity. */
    public void scheduleRoundDeadline(UUID sessionId, int roundNumber, Instant deadlineAt) {
        String key = key(sessionId, roundNumber);
        long delayMs = Math.max(0, Duration.between(clock.instant(), deadlineAt).toMillis());
        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                try {
                    endRound(sessionId, roundNumber, "time_limit");
                } catch (Exception e) {
                    log.error("Round deadline task failed for session {} round {}", sessionId, roundNumber, e);
                }
            },
            delayMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingDeadlines.put(key, future);
        if (old != null) old.cancel(false);
    }

    /** Called by RoundTurnService when the last turn finishes. Emits ROUND_TURNS_COMPLETE first for FE
     *  compatibility, then triggers the round-end path. */
    public void onTurnsComplete(UUID sessionId, int roundNumber) {
        eventPublisher.publish(sessionId.toString(), "ROUND_TURNS_COMPLETE",
            new RoundTurnsCompletePayload(roundNumber));
        endRound(sessionId, roundNumber, "turns_complete");
    }

    /** Single-shot round-end: stamps endedAt, decides startRound(k+1) vs vote, broadcasts ROUND_ENDED. */
    void endRound(UUID sessionId, int roundNumber, String reason) {
        String key = key(sessionId, roundNumber);
        if (endedRounds.putIfAbsent(key, Boolean.TRUE) != null) {
            log.debug("endRound already processed for session {} round {} — skipping", sessionId, roundNumber);
            return;
        }

        ScheduledFuture<?> pendingDeadline = pendingDeadlines.remove(key);
        if (pendingDeadline != null) pendingDeadline.cancel(false);

        EndContext ctx = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                || !"in_progress".equals(session.getPhase())
                || !"round".equals(session.getState())
                || !Integer.valueOf(roundNumber).equals(session.getCurrentRoundNumber())) {
                log.debug("endRound skipped: session {} not in expected round {} state", sessionId, roundNumber);
                return null;
            }

            RoundEntity round = roundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber).orElse(null);
            if (round == null) {
                log.warn("endRound: rounds row missing for session {} round {}", sessionId, roundNumber);
                return null;
            }

            Instant now = clock.instant();
            round.setEndedAt(now);
            roundRepository.save(round);

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElseThrow(
                () -> new IllegalStateException("Scenario not found: " + session.getScenarioId()));
            boolean isLast = roundNumber >= scenario.roundCount();

            if (isLast) {
                session.setState("vote");
                sessionRepository.saveAndFlush(session);
            }

            return new EndContext(now, isLast);
        });

        if (ctx == null) {
            endedRounds.remove(key); // allow a retry if TX failed
            return;
        }

        eventPublisher.publish(sessionId.toString(), "ROUND_ENDED",
            new RoundEndedPayload(roundNumber, reason, ctx.endedAt().toEpochMilli()));

        if (ctx.isLast()) {
            eventPublisher.publish(sessionId.toString(), "SESSION_STATE_CHANGED",
                new SessionStateChangedPayload("vote", null));
        } else {
            roundServiceProvider.getObject().startRound(sessionId, roundNumber + 1);
        }
    }

    @PreDestroy
    void shutdown() {
        cancelPendingDeadlinesForTest();
    }

    /** Test hook: cancel all pending deadline futures; call from @AfterEach in integration tests. */
    void cancelPendingDeadlinesForTest() {
        pendingDeadlines.forEach((k, f) -> f.cancel(false));
        pendingDeadlines.clear();
        endedRounds.clear();
    }

    private static String key(UUID sessionId, int roundNumber) {
        return sessionId + ":" + roundNumber;
    }

    private record EndContext(Instant endedAt, boolean isLast) {}
}
