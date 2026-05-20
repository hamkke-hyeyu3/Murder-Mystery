package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioCharacter.Mission;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CulpritRevealStartedPayload;
import com.murdermystery.ws.event.MissionPhaseStartedPayload;
import com.murdermystery.ws.event.MissionRevealedPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RevealService {

    private static final Logger log = LoggerFactory.getLogger(RevealService.class);

    private final SessionRepository sessionRepository;
    private final ScenarioRepository scenarioRepository;
    private final SessionEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler;

    @Value("${app.reveal.duration-ms:8000}")
    long revealDurationMs;

    // JVM-memory once-only guard (same pattern as RoundLifecycleService.endedRounds)
    private final ConcurrentHashMap<String, Boolean> startedReveals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingMissionPhases = new ConcurrentHashMap<>();

    public RevealService(
        SessionRepository sessionRepository,
        ScenarioRepository scenarioRepository,
        SessionEventPublisher eventPublisher,
        TransactionTemplate transactionTemplate,
        ScheduledExecutorService gameScheduler
    ) {
        this.sessionRepository = sessionRepository;
        this.scenarioRepository = scenarioRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = gameScheduler;
    }

    public void startReveal(UUID sessionId) {
        if (startedReveals.putIfAbsent(sessionId.toString(), Boolean.TRUE) != null) {
            log.debug("startReveal already running for session {} — skipping", sessionId);
            return;
        }

        RevealContext ctx = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;
            if (!"vote".equals(session.getState())) {
                log.debug("startReveal skipped: session {} state is '{}', expected 'vote'", sessionId, session.getState());
                return null;
            }
            String outcome = session.getVoteOutcome();
            if (outcome == null) {
                log.debug("startReveal skipped: session {} voteOutcome is null (tie still in progress)", sessionId);
                return null;
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            String culpritCharacterId;
            String accusedCharacterId;
            if ("single_winner".equals(outcome)) {
                culpritCharacterId = session.getVoteWinnerCharacterId();
                accusedCharacterId = culpritCharacterId;
            } else {
                // 'failed': reveal the true culprit declared by scenario author
                culpritCharacterId = scenario != null ? scenario.trueCulpritCharacterId() : null;
                accusedCharacterId = null;
            }

            session.setState("reveal");
            sessionRepository.save(session);

            return new RevealContext(
                session.getInviteCode(),
                outcome,
                culpritCharacterId,
                accusedCharacterId
            );
        });

        if (ctx == null) {
            startedReveals.remove(sessionId.toString());
            return;
        }

        eventPublisher.publish(sessionId.toString(), "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("reveal", null));
        eventPublisher.publish(sessionId.toString(), "CULPRIT_REVEAL_STARTED",
            new CulpritRevealStartedPayload(ctx.outcome(), ctx.culpritCharacterId(), ctx.accusedCharacterId()));

        ScheduledFuture<?> future = scheduler.schedule(
            () -> {
                try {
                    startMissionPhase(sessionId, ctx.inviteCode());
                } catch (Exception e) {
                    log.error("startMissionPhase failed for session {}", sessionId, e);
                }
            },
            revealDurationMs, TimeUnit.MILLISECONDS
        );
        ScheduledFuture<?> old = pendingMissionPhases.put(sessionId.toString(), future);
        if (old != null) old.cancel(false);
    }

    void startMissionPhase(UUID sessionId, String inviteCode) {
        MissionContext ctx = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;
            if (!"reveal".equals(session.getState())) {
                log.debug("startMissionPhase stale: session {} state is '{}', expected 'reveal'", sessionId, session.getState());
                return null;
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            Map<String, List<Mission>> missionsByCharId = scenario != null
                ? scenario.characters().stream()
                    .collect(Collectors.toMap(ScenarioCharacter::id,
                        c -> c.missions() != null ? c.missions() : List.of()))
                : Map.of();

            session.setState("mission");
            sessionRepository.save(session);

            // force eager load inside TX before returning
            List<Player> players = new ArrayList<>(session.getPlayers());
            return new MissionContext(players, missionsByCharId);
        });

        if (ctx == null) return;

        eventPublisher.publish(sessionId.toString(), "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("mission", null));
        eventPublisher.publish(sessionId.toString(), "MISSION_PHASE_STARTED",
            new MissionPhaseStartedPayload());

        for (Player player : ctx.players()) {
            String charId = player.getAssignedCharacterId();
            if (charId == null) continue;
            List<Mission> missions = ctx.missionsByCharId().getOrDefault(charId, List.of());
            eventPublisher.publishToPlayer(
                inviteCode,
                player.getId().toString(),
                sessionId.toString(),
                "MISSION_REVEALED",
                new MissionRevealedPayload(missions)
            );
        }
    }

    @PreDestroy
    void cancelPendingForTest() {
        pendingMissionPhases.forEach((k, f) -> f.cancel(false));
        pendingMissionPhases.clear();
    }

    private record RevealContext(
        String inviteCode,
        String outcome,
        String culpritCharacterId,
        String accusedCharacterId
    ) {}

    private record MissionContext(
        List<Player> players,
        Map<String, List<Mission>> missionsByCharId
    ) {}
}
