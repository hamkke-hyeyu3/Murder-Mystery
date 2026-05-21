package com.murdermystery.session;

import com.murdermystery.ws.event.ForceProgressAvailablePayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ForceProgressService {

    private static final Logger log = LoggerFactory.getLogger(ForceProgressService.class);
    private static final long OFFLINE_THRESHOLD_SECONDS = 30;

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final MissionEndingHelper endingHelper;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    @Value("${app.force-progress.delay-ms:180000}")
    long forceProgressDelayMs;

    // JVM-memory once-only guard (same pattern as RoundLifecycleService.endedRounds)
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingForceProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> evaluatedForceProgress = new ConcurrentHashMap<>();

    public ForceProgressService(
        SessionRepository sessionRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        MissionEndingHelper endingHelper,
        ScheduledExecutorService gameScheduler,
        Clock systemClock
    ) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.endingHelper = endingHelper;
        this.scheduler = gameScheduler;
        this.clock = systemClock;
    }

    /** Called when the first mission check completes. Schedules NB3 evaluation once. */
    public void scheduleEvaluation(UUID sessionId) {
        String key = sessionId.toString();
        if (pendingForceProgress.containsKey(key) || evaluatedForceProgress.containsKey(key)) {
            return; // already scheduled or already evaluated
        }
        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                try {
                    evaluate(sessionId);
                } catch (Exception e) {
                    log.error("forceProgress evaluate failed for session {}", sessionId, e);
                }
            },
            forceProgressDelayMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingForceProgress.putIfAbsent(key, future);
        if (old != null) {
            // race lost — another thread already registered
            future.cancel(false);
        }
    }

    /** Called when all players check complete naturally (no force needed). Cancels pending timer. */
    public void cancel(UUID sessionId) {
        String key = sessionId.toString();
        ScheduledFuture<?> f = pendingForceProgress.remove(key);
        if (f != null) f.cancel(false);
        evaluatedForceProgress.put(key, Boolean.TRUE); // block future schedule calls
    }

    /**
     * Executed by the scheduled timer. Evaluates NB3 conditions and broadcasts
     * FORCE_PROGRESS_AVAILABLE with scope 'host' or 'all'.
     */
    void evaluate(UUID sessionId) {
        String key = sessionId.toString();
        if (evaluatedForceProgress.putIfAbsent(key, Boolean.TRUE) != null) {
            return; // already evaluated
        }
        pendingForceProgress.remove(key);

        String scope = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null
                || !"in_progress".equals(session.getPhase())
                || !"mission".equals(session.getState())) {
                return null; // session gone or state changed
            }

            boolean anyIncomplete = session.getPlayers().stream()
                .anyMatch(p -> p.getMissionCheckedAt() == null);
            if (!anyIncomplete) {
                return null; // all completed naturally (race)
            }

            Player host = session.getPlayers().stream()
                .filter(Player::isHost).findFirst().orElse(null);
            if (host == null) return null;

            boolean hostChecked = host.getMissionCheckedAt() != null;
            if (hostChecked) {
                return "host";
            }

            Instant cutoff = clock.instant().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
            boolean offline = host.getLastSeenAt() == null || host.getLastSeenAt().isBefore(cutoff);
            return offline ? "all" : "host";
        });

        if (scope == null) return;

        eventPublisher.publish(sessionId.toString(), "FORCE_PROGRESS_AVAILABLE",
            new ForceProgressAvailablePayload(scope));
    }

    /**
     * Executes force progress: transitions the session to 'ending' state,
     * leaving incomplete players' mission_checked_at as null.
     *
     * <p>Accessible to the host at any time. Accessible to non-hosts only when
     * NB3 conditions hold: host is incomplete AND offline (last_seen_at < now-30s).
     * This narrow delegation applies only to this action — no other host actions
     * are affected (NB3 좁은 인계).
     */
    public void forceProgress(UUID sessionId, UUID requesterPlayerId) {
        transactionTemplate.executeWithoutResult(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

            if (!"in_progress".equals(session.getPhase()) || !"mission".equals(session.getState())) {
                // Already in ending or wrong phase — treat as idempotent no-op via exception
                throw new MissionPhaseRequiredException();
            }

            Player requester = session.getPlayers().stream()
                .filter(p -> requesterPlayerId.equals(p.getId()))
                .findFirst()
                .orElseThrow(PlayerNotInSessionException::new);

            if (!requester.isHost()) {
                checkNb3Authorization(session);
            }

            endingHelper.transitionToEnding(session);
        });

        cancel(sessionId);
        endingHelper.broadcastEndingTransition(sessionId.toString());
    }

    private void checkNb3Authorization(Session session) {
        Player host = session.getPlayers().stream()
            .filter(Player::isHost).findFirst().orElse(null);
        if (host == null) throw new ForceProgressNotAvailableException();

        boolean hostChecked = host.getMissionCheckedAt() != null;
        if (hostChecked) throw new ForceProgressNotAvailableException();

        Instant cutoff = clock.instant().minusSeconds(OFFLINE_THRESHOLD_SECONDS);
        boolean offline = host.getLastSeenAt() == null || host.getLastSeenAt().isBefore(cutoff);
        if (!offline) throw new ForceProgressNotAvailableException();
    }

    @PreDestroy
    void shutdown() {
        pendingForceProgress.forEach((k, f) -> f.cancel(false));
        pendingForceProgress.clear();
        evaluatedForceProgress.clear();
    }

    /** Package-private test hook. Call from @AfterEach. */
    void cancelPendingForTest() {
        pendingForceProgress.forEach((k, f) -> f.cancel(false));
        pendingForceProgress.clear();
        evaluatedForceProgress.clear();
    }
}
