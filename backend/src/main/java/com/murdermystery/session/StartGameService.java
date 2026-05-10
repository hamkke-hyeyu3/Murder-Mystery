package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CharacterCardDealtPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class StartGameService {

    private static final Logger log = LoggerFactory.getLogger(StartGameService.class);

    private final SessionRepository sessionRepository;
    private final ScenarioRepository scenarioRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    private final long characterAssignmentDelayMs;

    @Autowired
    public StartGameService(
        SessionRepository sessionRepository,
        ScenarioRepository scenarioRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService gameScheduler,
        @Value("${app.start.character-assignment-delay-ms:5000}") long characterAssignmentDelayMs
    ) {
        this(sessionRepository, scenarioRepository, transactionTemplate, eventPublisher,
            gameScheduler, null, characterAssignmentDelayMs);
    }

    StartGameService(
        SessionRepository sessionRepository,
        ScenarioRepository scenarioRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService scheduler,
        Random random,
        long characterAssignmentDelayMs
    ) {
        this.sessionRepository = sessionRepository;
        this.scenarioRepository = scenarioRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.random = random;
        this.characterAssignmentDelayMs = characterAssignmentDelayMs;
    }

    public StartGameResponse start(UUID sessionId, UUID requesterDeviceId) {
        StartResult result = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

            if (!"lobby".equals(session.getPhase())) {
                throw new SessionAlreadyStartedException();
            }

            Player host = session.getPlayers().stream()
                .filter(Player::isHost)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no host in session"));

            if (host.getDeviceId() == null || !host.getDeviceId().equals(requesterDeviceId)) {
                throw new NotHostException();
            }

            Scenario scenario = scenarioRepository.findById(session.getScenarioId())
                .orElseThrow(() -> new IllegalStateException("scenario not found: " + session.getScenarioId()));

            int required = scenario.characters().size();
            int joined = session.getPlayers().size();
            if (joined != required) {
                throw new LobbyCountMismatchException(joined, required);
            }

            List<ScenarioCharacter> shuffled = new ArrayList<>(scenario.characters());
            Collections.shuffle(shuffled, random != null ? random : ThreadLocalRandom.current());

            List<Player> sortedPlayers = session.getPlayers().stream()
                .sorted(Comparator.comparing(Player::getJoinedAt))
                .toList();

            for (int i = 0; i < sortedPlayers.size(); i++) {
                sortedPlayers.get(i).setAssignedCharacterId(shuffled.get(i).id());
            }

            List<String> turnOrder = shuffled.stream().map(ScenarioCharacter::id).toList();
            session.setPhase("in_progress");
            session.setState("intro");
            session.setTurnOrder(turnOrder);

            sessionRepository.saveAndFlush(session);

            return new StartResult(session.getId(), session.getInviteCode(), turnOrder);
        });

        eventPublisher.publish(
            result.sessionId().toString(),
            "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("intro", result.turnOrder())
        );

        scheduler.schedule(
            () -> transitionToCharacterAssignment(result.sessionId(), result.inviteCode()),
            characterAssignmentDelayMs,
            TimeUnit.MILLISECONDS
        );

        return new StartGameResponse(result.sessionId(), "in_progress", "intro", result.turnOrder());
    }

    void transitionToCharacterAssignment(UUID sessionId, String inviteCode) {
        List<PlayerCard> cards = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null
                    || !"in_progress".equals(session.getPhase())
                    || !"intro".equals(session.getState())) {
                return null;
            }
            session.setState("character_assignment");
            sessionRepository.saveAndFlush(session);

            List<String> turnOrder = session.getTurnOrder();
            if (turnOrder == null) {
                log.error("turnOrder is null for session {} in intro state — skipping card delivery", sessionId);
                return null;
            }
            return session.getPlayers().stream()
                .sorted(Comparator.comparing(Player::getJoinedAt))
                .map(p -> {
                    int index = turnOrder.indexOf(p.getAssignedCharacterId());
                    return new PlayerCard(p.getId().toString(), p.getAssignedCharacterId(), index);
                })
                .toList();
        });

        if (cards == null) {
            log.debug("character_assignment transition skipped for session {}", sessionId);
            return;
        }

        eventPublisher.publish(
            sessionId.toString(),
            "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("character_assignment", null)
        );

        Map<String, String> charNames = scenarioRepository.findAll().stream()
            .flatMap(s -> s.characters().stream())
            .collect(Collectors.toMap(ScenarioCharacter::id, ScenarioCharacter::name));

        for (PlayerCard card : cards) {
            String name = charNames.getOrDefault(card.characterId(), card.characterId());
            try {
                eventPublisher.publishToPlayer(
                    inviteCode,
                    card.playerId(),
                    sessionId.toString(),
                    "CHARACTER_CARD_DEALT",
                    new CharacterCardDealtPayload(card.characterId(), name, card.turnOrderIndex())
                );
            } catch (Exception e) {
                log.error("CHARACTER_CARD_DEALT delivery failed for player {} in session {}", card.playerId(), sessionId, e);
            }
        }
    }

    private record StartResult(UUID sessionId, String inviteCode, List<String> turnOrder) {}
    private record PlayerCard(String playerId, String characterId, int turnOrderIndex) {}
}
