package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CluePayload;
import com.murdermystery.ws.event.LocationAutoSelectedPayload;
import com.murdermystery.ws.event.LocationSelectedPayload;
import com.murdermystery.ws.event.ServerTimeSyncPayload;
import com.murdermystery.ws.event.TurnStartedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class RoundTurnService {

    private static final Logger log = LoggerFactory.getLogger(RoundTurnService.class);
    private static final long TURN_DEADLINE_SEC = 30L;
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(1);

    // Maps "sessionId:roundNumber:turnIndex" → scheduled autoSelect future (for cancel on manual select)
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingAutoSelects = new ConcurrentHashMap<>();
    // Maps same key → turn deadlineAt (for grace-period check in selectLocation)
    private final ConcurrentHashMap<String, Instant> turnDeadlines = new ConcurrentHashMap<>();

    private final SessionRepository sessionRepository;
    private final RoundRepository roundRepository;
    private final ScenarioRepository scenarioRepository;
    private final LocationOccupancyRepository occupancyRepository;
    private final ClueRepository clueRepository;
    private final ClueAclRepository clueAclRepository;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final Clock clock;
    private final RoundLifecycleService roundLifecycleService;

    @Autowired
    public RoundTurnService(
        SessionRepository sessionRepository,
        RoundRepository roundRepository,
        ScenarioRepository scenarioRepository,
        LocationOccupancyRepository occupancyRepository,
        ClueRepository clueRepository,
        ClueAclRepository clueAclRepository,
        PlayerRepository playerRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService gameScheduler,
        Clock systemClock,
        RoundLifecycleService roundLifecycleService
    ) {
        this(sessionRepository, roundRepository, scenarioRepository, occupancyRepository,
             clueRepository, clueAclRepository, playerRepository, transactionTemplate,
             eventPublisher, gameScheduler, null, systemClock, roundLifecycleService);
    }

    RoundTurnService(
        SessionRepository sessionRepository,
        RoundRepository roundRepository,
        ScenarioRepository scenarioRepository,
        LocationOccupancyRepository occupancyRepository,
        ClueRepository clueRepository,
        ClueAclRepository clueAclRepository,
        PlayerRepository playerRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService scheduler,
        Random random,
        Clock clock,
        RoundLifecycleService roundLifecycleService
    ) {
        this.sessionRepository = sessionRepository;
        this.roundRepository = roundRepository;
        this.scenarioRepository = scenarioRepository;
        this.occupancyRepository = occupancyRepository;
        this.clueRepository = clueRepository;
        this.clueAclRepository = clueAclRepository;
        this.playerRepository = playerRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.random = random;
        this.clock = clock;
        this.roundLifecycleService = roundLifecycleService;
    }

    public void startRoundTurns(UUID sessionId, int roundNumber) {
        if (roundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber).isEmpty()) {
            log.warn("startRoundTurns skipped: rounds row missing for session {} round {}", sessionId, roundNumber);
            return;
        }
        startTurn(sessionId, roundNumber, 0);
    }

    void startTurn(UUID sessionId, int roundNumber, int turnIndex) {
        TurnBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !Integer.valueOf(roundNumber).equals(session.getCurrentRoundNumber())) {
                log.debug("startTurn skipped for session {}: not in valid round state", sessionId);
                return null;
            }

            List<String> turnOrder = session.getTurnOrder();
            if (turnOrder == null || turnOrder.isEmpty()) {
                log.error("turnOrder null for session {} round {} — skipping turn", sessionId, roundNumber);
                return null;
            }

            String characterId = TurnQueueCalculator.characterIdAt(turnOrder, roundNumber, turnIndex);
            Player currentPlayer = session.getPlayers().stream()
                .filter(p -> characterId.equals(p.getAssignedCharacterId()))
                .findFirst()
                .orElse(null);
            if (currentPlayer == null) {
                log.error("No player with characterId {} in session {}", characterId, sessionId);
                return null;
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario == null) {
                log.error("Scenario not found for session {}", sessionId);
                return null;
            }

            List<LocationOccupancy> taken = occupancyRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber);
            List<String> occupiedLocations = taken.stream().map(LocationOccupancy::getLocationId).toList();
            List<String> candidates = new ArrayList<>(scenario.locationPool());
            candidates.removeAll(occupiedLocations);

            Instant deadlineAt = clock.instant().plus(Duration.ofSeconds(TURN_DEADLINE_SEC));

            return new TurnBundle(
                session.getInviteCode(),
                currentPlayer.getId(),
                currentPlayer.getNickname(),
                characterId,
                turnOrder.size(),
                candidates,
                deadlineAt
            );
        });

        if (bundle == null) return;

        Instant now = clock.instant();
        eventPublisher.publish(sessionId.toString(), "SERVER_TIME_SYNC",
            new ServerTimeSyncPayload(now.toEpochMilli()));
        eventPublisher.publish(sessionId.toString(), "TURN_STARTED",
            new TurnStartedPayload(
                roundNumber, turnIndex,
                bundle.playerId().toString(), bundle.characterId(),
                bundle.deadlineAt().toEpochMilli(),
                bundle.candidates()
            ));

        String futureKey = futureKey(sessionId, roundNumber, turnIndex);
        turnDeadlines.put(futureKey, bundle.deadlineAt());
        ScheduledFuture<?> future = scheduler.schedule(
            () -> autoSelectTurn(sessionId, roundNumber, turnIndex, bundle.playerCount()),
            TURN_DEADLINE_SEC, TimeUnit.SECONDS
        );
        ScheduledFuture<?> old = pendingAutoSelects.put(futureKey, future);
        if (old != null) {
            old.cancel(false);
        }
    }

    // Package-private so integration test can call directly (skipping real 30s wait)
    void autoSelectTurn(UUID sessionId, int roundNumber, int turnIndex, int playerCount) {
        AutoSelectBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !Integer.valueOf(roundNumber).equals(session.getCurrentRoundNumber())) {
                return null;
            }

            // Idempotency: if this player already selected, skip
            List<String> turnOrder = session.getTurnOrder();
            String characterId = TurnQueueCalculator.characterIdAt(turnOrder, roundNumber, turnIndex);
            Player currentPlayer = session.getPlayers().stream()
                .filter(p -> characterId.equals(p.getAssignedCharacterId()))
                .findFirst().orElse(null);
            if (currentPlayer == null) return null;

            if (occupancyRepository.existsBySessionIdAndRoundNumberAndPlayerId(
                    sessionId, roundNumber, currentPlayer.getId())) {
                log.debug("autoSelectTurn idempotent — player {} already selected", currentPlayer.getId());
                return null;
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario == null) return null;

            List<LocationOccupancy> taken = occupancyRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber);
            List<String> occupiedLocations = taken.stream().map(LocationOccupancy::getLocationId).toList();
            List<String> candidates = new ArrayList<>(scenario.locationPool());
            candidates.removeAll(occupiedLocations);
            if (candidates.isEmpty()) {
                log.error("No candidate locations left for autoSelect in session {} round {} turn {}", sessionId, roundNumber, turnIndex);
                return null;
            }

            Random rng = this.random != null ? this.random : ThreadLocalRandom.current();
            String locationId = candidates.get(rng.nextInt(candidates.size()));

            Instant now = clock.instant();
            occupancyRepository.save(new LocationOccupancy(
                sessionId, roundNumber, locationId,
                currentPlayer.getId(), characterId, now, true));

            // 1:1 assumption: each location has exactly one item (validated by ScenarioCrossFieldValidator)
            List<com.murdermystery.ws.event.CluePayload> clues = writeClues(
                sessionId, roundNumber, locationId, scenario, currentPlayer.getId(), now);

            return new AutoSelectBundle(
                session.getInviteCode(), currentPlayer.getId(), characterId,
                locationId, clues, turnOrder.size());
        });

        if (bundle == null) return;

        String autoKey = futureKey(sessionId, roundNumber, turnIndex);
        pendingAutoSelects.remove(autoKey);
        turnDeadlines.remove(autoKey);

        eventPublisher.publish(sessionId.toString(), "LOCATION_AUTO_SELECTED",
            new LocationAutoSelectedPayload(
                roundNumber, turnIndex,
                bundle.playerId().toString(), bundle.characterId(), bundle.locationId()));

        for (com.murdermystery.ws.event.CluePayload clue : bundle.clues()) {
            try {
                eventPublisher.publishToPlayer(
                    bundle.inviteCode(), bundle.playerId().toString(), sessionId.toString(),
                    "CLUE_DELIVERED", clue);
            } catch (Exception e) {
                log.error("CLUE_DELIVERED failed for player {} session {}", bundle.playerId(), sessionId, e);
            }
        }

        int nextIndex = turnIndex + 1;
        if (nextIndex < playerCount) {
            startTurn(sessionId, roundNumber, nextIndex);
        } else {
            roundLifecycleService.onTurnsComplete(sessionId, roundNumber);
        }
    }

    public void selectLocation(UUID sessionId, UUID playerId, String inviteCode,
                                String locationId, int roundNumber, int turnIndex) {
        String key = futureKey(sessionId, roundNumber, turnIndex);
        Instant deadline = turnDeadlines.get(key);
        if (deadline != null && clock.instant().isAfter(deadline.plus(GRACE_PERIOD))) {
            log.warn("selectLocation after grace: session {} round {} turn {}", sessionId, roundNumber, turnIndex);
            return;
        }

        SelectBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"round".equals(session.getState())
                    || !Integer.valueOf(roundNumber).equals(session.getCurrentRoundNumber())) {
                return null;
            }

            List<String> turnOrder = session.getTurnOrder();
            if (turnOrder == null || turnOrder.isEmpty()) return null;
            int n = turnOrder.size();
            if (turnIndex < 0 || turnIndex >= n) {
                log.warn("selectLocation rejected: stale turnIndex {} (n={}) session {}", turnIndex, n, sessionId);
                return null;
            }

            String expectedCharacterId = TurnQueueCalculator.characterIdAt(turnOrder, roundNumber, turnIndex);
            Player currentPlayer = session.getPlayers().stream()
                .filter(p -> expectedCharacterId.equals(p.getAssignedCharacterId()))
                .findFirst().orElse(null);
            if (currentPlayer == null || !currentPlayer.getId().equals(playerId)) {
                log.warn("selectLocation rejected: not player's turn in session {} round {} turn {}", sessionId, roundNumber, turnIndex);
                return null;
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario == null) return null;

            if (!scenario.locationPool().contains(locationId)) {
                log.warn("selectLocation rejected: location {} not in pool session {}", locationId, sessionId);
                return null;
            }

            List<LocationOccupancy> taken = occupancyRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber);
            if (taken.stream().anyMatch(o -> locationId.equals(o.getLocationId()))) {
                log.warn("selectLocation rejected: location {} already occupied session {} round {}", locationId, sessionId, roundNumber);
                return null;
            }

            Instant now = clock.instant();
            occupancyRepository.save(new LocationOccupancy(
                sessionId, roundNumber, locationId, playerId, expectedCharacterId, now, false));

            List<CluePayload> clues = writeClues(sessionId, roundNumber, locationId, scenario, playerId, now);

            return new SelectBundle(inviteCode, playerId, expectedCharacterId, locationId, clues, n);
        });

        if (bundle == null) return;

        ScheduledFuture<?> future = pendingAutoSelects.remove(key);
        if (future != null) future.cancel(false);
        turnDeadlines.remove(key);

        eventPublisher.publish(sessionId.toString(), "LOCATION_SELECTED",
            new LocationSelectedPayload(roundNumber, turnIndex,
                bundle.playerId().toString(), bundle.characterId(), bundle.locationId()));

        for (CluePayload clue : bundle.clues()) {
            try {
                eventPublisher.publishToPlayer(bundle.inviteCode(), bundle.playerId().toString(),
                    sessionId.toString(), "CLUE_DELIVERED", clue);
            } catch (Exception e) {
                log.error("CLUE_DELIVERED failed for player {} session {}", bundle.playerId(), sessionId, e);
            }
        }

        int nextIndex = turnIndex + 1;
        if (nextIndex < bundle.playerCount()) {
            startTurn(sessionId, roundNumber, nextIndex);
        } else {
            roundLifecycleService.onTurnsComplete(sessionId, roundNumber);
        }
    }

    private List<com.murdermystery.ws.event.CluePayload> writeClues(
            UUID sessionId, int roundNumber, String locationId,
            Scenario scenario, UUID playerId, Instant now) {
        // 1:1 assumption: one item per location (ScenarioCrossFieldValidator enforces items exist)
        List<com.murdermystery.ws.event.CluePayload> result = new ArrayList<>();
        for (var item : scenario.items()) {
            if (!locationId.equals(item.originLocationId())) continue;
            Clue clue = new Clue(sessionId, roundNumber, item.id(), locationId, item.title(), playerId, now);
            clueRepository.save(clue);
            clueAclRepository.save(new ClueAcl(clue.getId(), playerId, now, "discovery"));
            result.add(new com.murdermystery.ws.event.CluePayload(
                clue.getId().toString(), item.id(), item.title(), locationId,
                roundNumber, now.toEpochMilli(), "discovery"));
        }
        return result;
    }

    private static String futureKey(UUID sessionId, int roundNumber, int turnIndex) {
        return sessionId + ":" + roundNumber + ":" + turnIndex;
    }

    public java.util.Optional<Instant> getTurnDeadline(UUID sessionId, int roundNumber, int turnIndex) {
        return java.util.Optional.ofNullable(turnDeadlines.get(futureKey(sessionId, roundNumber, turnIndex)));
    }

    // Package-private: set a fake deadline for unit tests that need to exercise grace-period logic
    void putDeadlineForTest(UUID sessionId, int roundNumber, int turnIndex, Instant deadline) {
        turnDeadlines.put(futureKey(sessionId, roundNumber, turnIndex), deadline);
    }

    // Package-private: cancel all pending auto-select futures; call from @AfterEach to prevent
    // post-test timer firings when Spring context is shared across integration tests
    void cancelPendingAutoSelectsForTest() {
        pendingAutoSelects.forEach((key, future) -> future.cancel(false));
        pendingAutoSelects.clear();
    }

    private record TurnBundle(
        String inviteCode, UUID playerId, String nickname, String characterId,
        int playerCount, List<String> candidates, Instant deadlineAt
    ) {}

    private record AutoSelectBundle(
        String inviteCode, UUID playerId, String characterId,
        String locationId, List<com.murdermystery.ws.event.CluePayload> clues, int playerCount
    ) {}

    private record SelectBundle(
        String inviteCode, UUID playerId, String characterId,
        String locationId, List<CluePayload> clues, int playerCount
    ) {}
}
