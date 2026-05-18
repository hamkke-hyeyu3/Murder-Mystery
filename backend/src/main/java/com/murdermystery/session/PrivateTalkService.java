package com.murdermystery.session;

import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.PrivateTalkEndedPayload;
import com.murdermystery.ws.event.PrivateTalkRequestedPayload;
import com.murdermystery.ws.event.PrivateTalkStartedPayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class PrivateTalkService {

    private static final Logger log = LoggerFactory.getLogger(PrivateTalkService.class);
    private static final long REQUEST_TIMEOUT_SECONDS = 60;

    private final SessionRepository sessionRepository;
    private final PrivateTalkRepository privateTalkRepository;
    private final ScenarioRepository scenarioRepository;
    private final SessionEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingTimeouts = new ConcurrentHashMap<>();

    public PrivateTalkService(SessionRepository sessionRepository,
                              PrivateTalkRepository privateTalkRepository,
                              ScenarioRepository scenarioRepository,
                              SessionEventPublisher eventPublisher,
                              TransactionTemplate transactionTemplate,
                              ScheduledExecutorService scheduler,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.privateTalkRepository = privateTalkRepository;
        this.scenarioRepository = scenarioRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Handles a private talk request. Sends PRIVATE_TALK_REQUESTED to both
     * requester and target via their private queues. Schedules a 60s server-side
     * timeout (silent — no event on expiry).
     */
    public void request(UUID sessionId, UUID requesterId, UUID targetId) {
        if (requesterId == null || targetId == null) return;
        if (requesterId.equals(targetId)) return;

        RequestBundle bundle;
        try {
            bundle = transactionTemplate.execute(status -> {
                Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
                if (session == null
                        || !"in_progress".equals(session.getPhase())
                        || !"round".equals(session.getState())) return null;

                List<Player> players = session.getPlayers();
                Player requester = findPlayer(players, requesterId);
                Player target = findPlayer(players, targetId);
                if (requester == null || target == null) return null;

                boolean allowed = scenarioRepository.findById(session.getScenarioId())
                        .map(sc -> sc.allowPrivateTalk())
                        .orElse(false);
                if (!allowed) return null;

                if (privateTalkRepository.findActiveBySessionId(sessionId).isPresent()) return null;

                Instant now = clock.instant();
                PrivateTalk talk = new PrivateTalk(sessionId, session.getCurrentRoundNumber(),
                        requesterId, targetId, now);
                privateTalkRepository.save(talk);

                return new RequestBundle(session.getInviteCode(), talk, requester, target, now);
            });
        } catch (DataIntegrityViolationException e) {
            log.debug("private talk request race — already active for session {}", sessionId);
            return;
        }

        if (bundle == null) return;

        Instant expiresAt = bundle.requestedAt().plusSeconds(REQUEST_TIMEOUT_SECONDS);
        PrivateTalkRequestedPayload payload = new PrivateTalkRequestedPayload(
                bundle.talk().getId().toString(),
                requesterId.toString(), bundle.requester().getNickname(),
                targetId.toString(), bundle.target().getNickname(),
                bundle.requestedAt().toEpochMilli(), expiresAt.toEpochMilli());

        eventPublisher.publishToPlayer(bundle.inviteCode(), requesterId.toString(),
                sessionId.toString(), "PRIVATE_TALK_REQUESTED", payload);
        eventPublisher.publishToPlayer(bundle.inviteCode(), targetId.toString(),
                sessionId.toString(), "PRIVATE_TALK_REQUESTED", payload);

        UUID talkId = bundle.talk().getId();
        ScheduledFuture<?> future = scheduler.schedule(
                () -> timeoutRequest(talkId), REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        pendingTimeouts.put(talkId, future);
    }

    /**
     * Accepts a pending private talk request. Emits PRIVATE_TALK_STARTED broadcast.
     */
    public void accept(UUID sessionId, UUID requestId, UUID accepterId) {
        if (requestId == null || accepterId == null) return;

        AcceptBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;

            PrivateTalk talk = privateTalkRepository.findById(requestId).orElse(null);
            if (talk == null || !talk.isActive()) return null;
            if (!talk.getTargetPlayerId().equals(accepterId)) return null;

            Player requester = findPlayer(session.getPlayers(), talk.getRequesterPlayerId());
            Player target = findPlayer(session.getPlayers(), talk.getTargetPlayerId());
            if (requester == null || target == null) return null;

            Instant now = clock.instant();
            talk.markStarted(now);
            privateTalkRepository.save(talk);

            return new AcceptBundle(talk, requester, target, now);
        });

        if (bundle == null) return;

        cancelTimeout(bundle.talk().getId());

        List<PrivateTalkStartedPayload.Participant> participants = List.of(
                new PrivateTalkStartedPayload.Participant(
                        bundle.requester().getId().toString(), bundle.requester().getNickname()),
                new PrivateTalkStartedPayload.Participant(
                        bundle.target().getId().toString(), bundle.target().getNickname()));

        eventPublisher.publish(sessionId.toString(), "PRIVATE_TALK_STARTED",
                new PrivateTalkStartedPayload(bundle.talk().getId().toString(),
                        participants, bundle.startedAt().toEpochMilli()));
    }

    /**
     * Rejects a pending private talk request. No event is emitted — silent by spec.
     */
    public void reject(UUID sessionId, UUID requestId, UUID rejecterId) {
        if (requestId == null || rejecterId == null) return;

        UUID talkId = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;

            PrivateTalk talk = privateTalkRepository.findById(requestId).orElse(null);
            if (talk == null || !talk.isActive()) return null;
            if (!talk.getTargetPlayerId().equals(rejecterId)) return null;

            talk.markEnded("REJECTED", clock.instant());
            privateTalkRepository.save(talk);
            return talk.getId();
        });

        if (talkId != null) cancelTimeout(talkId);
    }

    /**
     * Ends an active (started) private talk. Emits PRIVATE_TALK_ENDED broadcast.
     */
    public void end(UUID sessionId, UUID requestId, UUID enderId) {
        if (requestId == null || enderId == null) return;

        EndBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null) return null;

            PrivateTalk talk = privateTalkRepository.findById(requestId).orElse(null);
            if (talk == null || !talk.isActive() || !talk.isStarted()) return null;
            boolean isParticipant = talk.getRequesterPlayerId().equals(enderId)
                    || talk.getTargetPlayerId().equals(enderId);
            if (!isParticipant) return null;

            Player requester = findPlayer(session.getPlayers(), talk.getRequesterPlayerId());
            Player target = findPlayer(session.getPlayers(), talk.getTargetPlayerId());
            if (requester == null || target == null) return null;

            Instant now = clock.instant();
            talk.markEnded("USER_ENDED", now);
            privateTalkRepository.save(talk);

            return new EndBundle(talk, requester, target, now);
        });

        if (bundle == null) return;

        publishEnded(sessionId, bundle.talk(), bundle.requester(), bundle.target(),
                bundle.endedAt(), "USER_ENDED");
    }

    /** Server-side timeout callback — records TIMEOUT end reason, emits no event. */
    void timeoutRequest(UUID requestId) {
        pendingTimeouts.remove(requestId);

        transactionTemplate.execute(status -> {
            PrivateTalk talk = privateTalkRepository.findById(requestId).orElse(null);
            if (talk == null || !talk.isActive()) return null;
            talk.markEnded("TIMEOUT", clock.instant());
            privateTalkRepository.save(talk);
            return null;
        });
    }

    /**
     * Ends any active private talk when a round boundary is reached.
     * Emits PRIVATE_TALK_ENDED broadcast. Called from RoundLifecycleService
     * before ROUND_ENDED so FE clears the banner before processing round transition.
     */
    void endByRoundBoundary(UUID sessionId, int roundNumber) {
        Optional<PrivateTalk> active = privateTalkRepository.findActiveBySessionId(sessionId);
        if (active.isEmpty()) return;

        PrivateTalk talk = active.get();
        cancelTimeout(talk.getId());

        EndBundle bundle = transactionTemplate.execute(status -> {
            PrivateTalk t = privateTalkRepository.findById(talk.getId()).orElse(null);
            if (t == null || !t.isActive()) return null;

            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) return null;

            Player requester = findPlayer(session.getPlayers(), t.getRequesterPlayerId());
            Player target = findPlayer(session.getPlayers(), t.getTargetPlayerId());

            Instant now = clock.instant();
            t.markEnded("ROUND_BOUNDARY", now);
            privateTalkRepository.save(t);

            return new EndBundle(t, requester, target, now);
        });

        if (bundle == null) return;

        publishEnded(sessionId, bundle.talk(), bundle.requester(), bundle.target(),
                bundle.endedAt(), "ROUND_BOUNDARY");
    }

    @PreDestroy
    void cancelPendingTimeoutsForTest() {
        pendingTimeouts.forEach((id, f) -> f.cancel(false));
        pendingTimeouts.clear();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private void publishEnded(UUID sessionId, PrivateTalk talk,
                               Player requester, Player target, Instant endedAt, String reason) {
        List<PrivateTalkStartedPayload.Participant> participants = List.of(
                new PrivateTalkStartedPayload.Participant(
                        requester != null ? requester.getId().toString() : talk.getRequesterPlayerId().toString(),
                        requester != null ? requester.getNickname() : ""),
                new PrivateTalkStartedPayload.Participant(
                        target != null ? target.getId().toString() : talk.getTargetPlayerId().toString(),
                        target != null ? target.getNickname() : ""));

        eventPublisher.publish(sessionId.toString(), "PRIVATE_TALK_ENDED",
                new PrivateTalkEndedPayload(talk.getId().toString(), participants,
                        reason, endedAt.toEpochMilli()));
    }

    private void cancelTimeout(UUID talkId) {
        ScheduledFuture<?> future = pendingTimeouts.remove(talkId);
        if (future != null) future.cancel(false);
    }

    private Player findPlayer(List<Player> players, UUID playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst().orElse(null);
    }

    // ── Bundle records ───────────────────────────────────────────────────

    private record RequestBundle(String inviteCode, PrivateTalk talk,
                                  Player requester, Player target, Instant requestedAt) {}

    private record AcceptBundle(PrivateTalk talk, Player requester, Player target, Instant startedAt) {}

    private record EndBundle(PrivateTalk talk, Player requester, Player target, Instant endedAt) {}
}
