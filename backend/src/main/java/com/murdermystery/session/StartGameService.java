package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioLocation;
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
    private final TutorialService tutorialService;
    private final Random random;
    private final long characterAssignmentDelayMs;
    private final long tutorialEnterDelayMs;

    @Autowired
    public StartGameService(
        SessionRepository sessionRepository,
        ScenarioRepository scenarioRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService gameScheduler,
        TutorialService tutorialService,
        @Value("${app.start.character-assignment-delay-ms:5000}") long characterAssignmentDelayMs,
        @Value("${app.tutorial.enter-delay-ms:5000}") long tutorialEnterDelayMs
    ) {
        this(sessionRepository, scenarioRepository, transactionTemplate, eventPublisher,
            gameScheduler, tutorialService, null, characterAssignmentDelayMs, tutorialEnterDelayMs);
    }

    StartGameService(
        SessionRepository sessionRepository,
        ScenarioRepository scenarioRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScheduledExecutorService scheduler,
        TutorialService tutorialService,
        Random random,
        long characterAssignmentDelayMs,
        long tutorialEnterDelayMs
    ) {
        this.sessionRepository = sessionRepository;
        this.scenarioRepository = scenarioRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        this.tutorialService = tutorialService;
        this.random = random;
        this.characterAssignmentDelayMs = characterAssignmentDelayMs;
        this.tutorialEnterDelayMs = tutorialEnterDelayMs;
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
        CardBundle cards = transactionTemplate.execute(status -> {
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
            List<PlayerCard> playerCards = session.getPlayers().stream()
                .sorted(Comparator.comparing(Player::getJoinedAt))
                .map(p -> {
                    int index = turnOrder.indexOf(p.getAssignedCharacterId());
                    return new PlayerCard(p.getId().toString(), p.getAssignedCharacterId(), index);
                })
                .toList();
            return new CardBundle(session.getScenarioId(), playerCards);
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

        scheduler.schedule(
            () -> tutorialService.enterTutorialState(sessionId),
            tutorialEnterDelayMs,
            TimeUnit.MILLISECONDS
        );

        Scenario scenario = scenarioRepository.findById(cards.scenarioId()).orElse(null);
        if (scenario == null) {
            log.warn("Scenario {} not found during card delivery for session {} — character names will fall back to characterId",
                cards.scenarioId(), sessionId);
        }
        Map<String, ScenarioCharacter> charMap = (scenario == null || scenario.characters() == null) ? Map.of() :
            scenario.characters().stream().collect(Collectors.toMap(ScenarioCharacter::id, c -> c, (a, b) -> a));
        Map<String, ScenarioLocation> locMap = (scenario == null || scenario.locations() == null) ? Map.of() :
            scenario.locations().stream().collect(Collectors.toMap(ScenarioLocation::id, l -> l, (a, b) -> a));

        for (PlayerCard card : cards.cards()) {
            ScenarioCharacter ch = charMap.get(card.characterId());
            String name = ch != null ? ch.name() : card.characterId();
            CharacterCardDealtPayload.LocationRef alibiLocation = buildAlibiLocation(ch, locMap);
            try {
                eventPublisher.publishToPlayer(
                    inviteCode,
                    card.playerId(),
                    sessionId.toString(),
                    "CHARACTER_CARD_DEALT",
                    new CharacterCardDealtPayload(
                        card.characterId(), name, card.turnOrderIndex(),
                        ch != null ? ch.speechStyle() : null,
                        ch != null ? ch.background() : null,
                        ch != null ? ch.motive() : null,
                        ch != null ? ch.alibi() : null,
                        ch != null ? ch.secret() : null,
                        ch != null ? ch.relationships() : null,
                        alibiLocation,
                        List.of()
                    )
                );
            } catch (Exception e) {
                log.error("CHARACTER_CARD_DEALT delivery failed for player {} in session {}", card.playerId(), sessionId, e);
            }
        }
    }

    private CharacterCardDealtPayload.LocationRef buildAlibiLocation(
            ScenarioCharacter ch, Map<String, ScenarioLocation> locMap) {
        if (ch == null || ch.alibiLocationId() == null) return null;
        ScenarioLocation loc = locMap.get(ch.alibiLocationId());
        if (loc == null) return null;
        return new CharacterCardDealtPayload.LocationRef(loc.id(), loc.name(), loc.icon());
    }

    private record StartResult(UUID sessionId, String inviteCode, List<String> turnOrder) {}
    private record CardBundle(String scenarioId, List<PlayerCard> cards) {}
    private record PlayerCard(String playerId, String characterId, int turnOrderIndex) {}
}
