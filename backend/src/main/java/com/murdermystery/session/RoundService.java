package com.murdermystery.session;

import com.murdermystery.scenario.Round;
import com.murdermystery.scenario.RoundObjective;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.ObjectiveUpdatedPayload;
import com.murdermystery.ws.event.RoundStartedPayload;
import com.murdermystery.ws.event.ServerTimeSyncPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class RoundService {

    public static final String DEFAULT_K1_INTRO_PROMPT = "한 사람씩 자기 캐릭터를 짧게 소개해 주세요.";

    private static final Logger log = LoggerFactory.getLogger(RoundService.class);

    private final SessionRepository sessionRepository;
    private final RoundRepository roundRepository;
    private final ScenarioRepository scenarioRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;

    public RoundService(
        SessionRepository sessionRepository,
        RoundRepository roundRepository,
        ScenarioRepository scenarioRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher
    ) {
        this.sessionRepository = sessionRepository;
        this.roundRepository = roundRepository;
        this.scenarioRepository = scenarioRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    public void startRound(UUID sessionId, int roundNumber) {
        RoundEntry entry = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
            if (session == null || !"in_progress".equals(session.getPhase()) || !"round".equals(session.getState())) {
                log.debug("startRound skipped for session {}: not in round state", sessionId);
                return null;
            }

            Integer current = session.getCurrentRoundNumber();
            boolean alreadyAtRound = Integer.valueOf(roundNumber).equals(current);
            boolean expectedNext = (current == null && roundNumber == 1)
                || (current != null && current == roundNumber - 1);

            if (!alreadyAtRound && !expectedNext) {
                log.debug("startRound skipped for session {}: currentRound={} requested={}", sessionId, current, roundNumber);
                return null;
            }

            if (alreadyAtRound) {
                // Idempotent re-broadcast: load existing round data (OBJECTIVE_UPDATED skipped — handled by T-18 snapshot)
                return roundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber)
                    .map(r -> new RoundEntry(roundNumber, r.getPrompt(), r.getCommonHint(),
                        r.getStartedAt().toEpochMilli(), r.getDeadlineAt().toEpochMilli(),
                        session.getInviteCode(), sessionId.toString(), 0, List.of()))
                    .orElse(null);
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId())
                .orElseThrow(() -> new IllegalStateException("Scenario not found: " + session.getScenarioId()));

            if (scenario.rounds() == null || roundNumber > scenario.rounds().size()) {
                throw new IllegalStateException("Scenario " + scenario.id() + " has no round " + roundNumber);
            }

            Round roundMeta = scenario.rounds().get(roundNumber - 1);
            String prompt = (roundNumber == 1 && (roundMeta.prompt() == null || roundMeta.prompt().isBlank()))
                ? DEFAULT_K1_INTRO_PROMPT
                : roundMeta.prompt();

            Instant now = Instant.now();
            Instant deadline = now.plus(Duration.ofSeconds(roundMeta.timeLimitSec()));

            roundRepository.save(new RoundEntity(sessionId, roundNumber, prompt, roundMeta.commonHint(), now, deadline));
            session.setCurrentRoundNumber(roundNumber);
            sessionRepository.saveAndFlush(session);

            String inviteCode = session.getInviteCode();
            int totalRounds = scenario.roundCount();
            Map<String, String> charObjectiveMap = new HashMap<>();
            scenario.characters().stream()
                .filter(c -> c.objectivesByRound() != null)
                .forEach(c -> charObjectiveMap.put(
                    c.id(),
                    c.objectivesByRound().stream()
                        .filter(o -> o.round() == roundNumber)
                        .findFirst()
                        .map(RoundObjective::text)
                        .orElse(null)
                ));
            List<PlayerObjective> playerObjectives = session.getPlayers().stream()
                .filter(p -> p.getAssignedCharacterId() != null)
                .map(p -> new PlayerObjective(
                    p.getId().toString(),
                    charObjectiveMap.getOrDefault(p.getAssignedCharacterId(), null)
                ))
                .toList();

            return new RoundEntry(roundNumber, prompt, roundMeta.commonHint(),
                now.toEpochMilli(), deadline.toEpochMilli(),
                inviteCode, sessionId.toString(), totalRounds, playerObjectives);
        });

        if (entry == null) return;

        eventPublisher.publish(sessionId.toString(), "SERVER_TIME_SYNC",
            new ServerTimeSyncPayload(entry.startedAt()));
        eventPublisher.publish(sessionId.toString(), "ROUND_STARTED",
            new RoundStartedPayload(entry.roundNumber(), entry.prompt(), entry.commonHint(),
                entry.deadlineAt(), entry.startedAt()));

        for (PlayerObjective po : entry.playerObjectives()) {
            try {
                eventPublisher.publishToPlayer(
                    entry.inviteCode(), po.playerId(), entry.sessionIdStr(),
                    "OBJECTIVE_UPDATED",
                    new ObjectiveUpdatedPayload(entry.roundNumber(), entry.totalRounds(), po.objective())
                );
            } catch (Exception e) {
                log.error("OBJECTIVE_UPDATED delivery failed for player {} in session {}", po.playerId(), sessionId, e);
            }
        }
    }

    private record RoundEntry(
        int roundNumber, String prompt, String commonHint, long startedAt, long deadlineAt,
        String inviteCode, String sessionIdStr, int totalRounds, List<PlayerObjective> playerObjectives
    ) {}
    private record PlayerObjective(String playerId, String objective) {}
}
