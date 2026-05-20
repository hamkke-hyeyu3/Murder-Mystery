package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.RunoffStartedPayload;
import com.murdermystery.ws.event.VoteProgressPayload;
import com.murdermystery.ws.event.VoteResultPayload;
import com.murdermystery.ws.event.VoteStartedPayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VoteService {

    private static final Logger log = LoggerFactory.getLogger(VoteService.class);
    static final long VOTE_DEADLINE_SECONDS = 60;

    private final SessionRepository sessionRepository;
    private final VoteRepository voteRepository;
    private final ScenarioRepository scenarioRepository;
    private final SessionEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    // key = sessionId:roundNo
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDeadlines = new ConcurrentHashMap<>();

    public VoteService(SessionRepository sessionRepository,
                       VoteRepository voteRepository,
                       ScenarioRepository scenarioRepository,
                       SessionEventPublisher eventPublisher,
                       TransactionTemplate transactionTemplate,
                       ScheduledExecutorService scheduler,
                       Clock clock) {
        this.sessionRepository = sessionRepository;
        this.voteRepository = voteRepository;
        this.scenarioRepository = scenarioRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** Called by RoundLifecycleService when the last round ends (state='vote' already set). */
    public void startVoteRound(UUID sessionId, int roundNo) {
        StartBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || !"in_progress".equals(session.getPhase())
                    || !"vote".equals(session.getState())) return null;

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario == null) return null;

            List<String> candidates = scenario.characters().stream()
                    .map(c -> c.id()).collect(Collectors.toList());
            Instant deadlineAt = clock.instant().plusSeconds(VOTE_DEADLINE_SECONDS);
            session.setVoteRoundNo(roundNo);
            session.setVoteDeadlineAt(deadlineAt);
            sessionRepository.save(session);

            return new StartBundle(sessionId.toString(), candidates, deadlineAt);
        });

        if (bundle == null) return;

        scheduleDeadline(sessionId, roundNo, bundle.deadlineAt());

        eventPublisher.publish(bundle.sessionId(), "VOTE_STARTED",
                new VoteStartedPayload(roundNo, bundle.deadlineAt().toEpochMilli(), bundle.candidates()));
    }

    /**
     * STOMP handler entry point: UPSERT vote, broadcast VOTE_PROGRESS, auto-tally if all submitted.
     * submittedRoundNo must match the current session vote round to prevent stale round-0 submits
     * landing in round-1 after the deadline has advanced the round.
     */
    public void submit(UUID sessionId, UUID voterPlayerId, String targetCharacterId,
                       int submittedRoundNo, String inviteCode) {
        SubmitBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"vote".equals(session.getState())) return null;
            if (session.getVoteOutcome() != null) return null;

            int currentRoundNo = session.getVoteRoundNo() != null ? session.getVoteRoundNo() : 0;
            if (submittedRoundNo != currentRoundNo) return null; // stale submit — silent ignore

            Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario == null) return null;

            Set<String> validCharIds = scenario.characters().stream()
                    .map(c -> c.id()).collect(Collectors.toSet());
            if (!validCharIds.contains(targetCharacterId)) return null;

            List<Player> players = session.getPlayers();
            Player voter = findPlayer(players, voterPlayerId);
            if (voter == null) return null;

            List<Vote> existing = voteRepository.findBySessionIdAndRoundNo(sessionId, currentRoundNo);

            Vote vote = existing.stream()
                    .filter(v -> v.getVoterPlayerId().equals(voterPlayerId))
                    .findFirst()
                    .orElse(null);
            if (vote == null) {
                vote = new Vote(sessionId, currentRoundNo, voterPlayerId, targetCharacterId, clock.instant());
            } else {
                vote.setTargetCharacterId(targetCharacterId);
                vote.setVotedAt(clock.instant());
            }
            voteRepository.save(vote);

            long submittedCount = existing.stream()
                    .map(Vote::getVoterPlayerId)
                    .collect(Collectors.toSet())
                    .size();
            // include new voter if not already counted
            if (existing.stream().noneMatch(v -> v.getVoterPlayerId().equals(voterPlayerId))) {
                submittedCount++;
            }
            int totalCount = players.size();

            return new SubmitBundle(sessionId.toString(), currentRoundNo,
                    (int) submittedCount, totalCount);
        });

        if (bundle == null) return;

        eventPublisher.publish(bundle.sessionId(), "VOTE_PROGRESS",
                new VoteProgressPayload(bundle.roundNo(), bundle.submittedCount(), bundle.totalCount()));

        if (bundle.submittedCount() >= bundle.totalCount()) {
            tally(sessionId, bundle.roundNo());
        }
    }

    /** Aggregate votes for the given round. Three branches: single_winner / tie / failed. */
    void tally(UUID sessionId, int roundNo) {
        TallyContext ctx = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;
            if (session.getVoteOutcome() != null) return null; // idempotent: outcome already determined

            int currentRoundNo = session.getVoteRoundNo() != null ? session.getVoteRoundNo() : 0;
            // Stale tally: this round was already processed and the session has moved forward
            if (roundNo < currentRoundNo) return null;

            List<Vote> votes = voteRepository.findBySessionIdAndRoundNo(sessionId, roundNo);

            Map<String, Integer> counts = new HashMap<>();
            for (Vote v : votes) {
                counts.merge(v.getTargetCharacterId(), 1, Integer::sum);
            }

            int maxCount = counts.values().stream().mapToInt(i -> i).max().orElse(0);
            List<String> leaders;
            if (maxCount == 0) {
                // all abstained — load all scenario characters as tied
                Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
                leaders = scenario != null
                        ? scenario.characters().stream().map(c -> c.id()).collect(Collectors.toList())
                        : new ArrayList<>();
            } else {
                leaders = counts.entrySet().stream()
                        .filter(e -> e.getValue() == maxCount)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }

            List<VoteResultPayload.TallyEntry> tally = counts.entrySet().stream()
                    .map(e -> new VoteResultPayload.TallyEntry(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            if (leaders.size() == 1) {
                session.setVoteOutcome("single_winner");
                session.setVoteWinnerCharacterId(leaders.get(0));
                sessionRepository.save(session);
                return new TallyContext(sessionId.toString(), roundNo, "single_winner",
                        leaders.get(0), null, tally, null, null);
            } else if (roundNo == 0) {
                // first-round tie → advance to runoff; voteOutcome stays null until runoff resolves
                Instant deadlineAt = clock.instant().plusSeconds(VOTE_DEADLINE_SECONDS);
                session.setVoteRoundNo(1);
                session.setVoteDeadlineAt(deadlineAt);
                sessionRepository.save(session);

                Scenario scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
                List<String> allCandidates = scenario != null
                        ? scenario.characters().stream().map(c -> c.id()).collect(Collectors.toList())
                        : leaders;

                return new TallyContext(sessionId.toString(), roundNo, "tie",
                        null, leaders, tally, allCandidates, deadlineAt);
            } else {
                session.setVoteOutcome("failed");
                sessionRepository.save(session);
                return new TallyContext(sessionId.toString(), roundNo, "failed",
                        null, leaders, tally, null, null);
            }
        });

        if (ctx == null) return;

        eventPublisher.publish(ctx.sessionId(), "VOTE_RESULT",
                new VoteResultPayload(ctx.roundNo(), ctx.outcome(),
                        ctx.winnerCharacterId(), ctx.tiedCharacterIds(), ctx.tally()));

        if ("tie".equals(ctx.outcome())) {
            scheduleDeadline(UUID.fromString(ctx.sessionId()), 1, ctx.runoffDeadlineAt());
            eventPublisher.publish(ctx.sessionId(), "RUNOFF_STARTED",
                    new RunoffStartedPayload(1, ctx.runoffDeadlineAt().toEpochMilli(),
                            ctx.runoffCandidates(), ctx.tiedCharacterIds()));
        }
    }

    private void scheduleDeadline(UUID sessionId, int roundNo, Instant deadlineAt) {
        String key = sessionId + ":" + roundNo;
        long delayMs = Math.max(0,
                deadlineAt.toEpochMilli() - clock.instant().toEpochMilli());
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    try {
                        tally(sessionId, roundNo);
                    } catch (Exception e) {
                        log.error("Vote deadline tally failed for session {} round {}", sessionId, roundNo, e);
                    }
                },
                delayMs, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> old = pendingDeadlines.put(key, future);
        if (old != null) old.cancel(false);
    }

    @PreDestroy
    void cancelPendingForTest() {
        pendingDeadlines.forEach((k, f) -> f.cancel(false));
        pendingDeadlines.clear();
    }

    private Player findPlayer(List<Player> players, UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst().orElse(null);
    }

    // ── Bundle records ───────────────────────────────────────────────────

    private record StartBundle(String sessionId, List<String> candidates, Instant deadlineAt) {}

    private record SubmitBundle(String sessionId, int roundNo, int submittedCount, int totalCount) {}

    private record TallyContext(
            String sessionId, int roundNo, String outcome,
            String winnerCharacterId, List<String> tiedCharacterIds,
            List<VoteResultPayload.TallyEntry> tally,
            List<String> runoffCandidates, Instant runoffDeadlineAt) {}
}
