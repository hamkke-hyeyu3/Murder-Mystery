package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioLocation;
import com.murdermystery.scenario.ScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int INVITE_CODE_RETRY_LIMIT = 5;
    private static final Set<String> CARD_VISIBLE_STATES = Set.of("character_assignment", "tutorial", "round");

    private final ScenarioRepository scenarioRepository;
    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final RoundRepository roundRepository;
    private final LocationOccupancyRepository occupancyRepository;
    private final ClueRepository clueRepository;
    private final ClueAclRepository clueAclRepository;
    private final RoundTurnService roundTurnService;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final TransactionTemplate transactionTemplate;

    public SessionService(
        ScenarioRepository scenarioRepository,
        SessionRepository sessionRepository,
        PlayerRepository playerRepository,
        RoundRepository roundRepository,
        LocationOccupancyRepository occupancyRepository,
        ClueRepository clueRepository,
        ClueAclRepository clueAclRepository,
        RoundTurnService roundTurnService,
        InviteCodeGenerator inviteCodeGenerator,
        TransactionTemplate transactionTemplate
    ) {
        this.scenarioRepository = scenarioRepository;
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.roundRepository = roundRepository;
        this.occupancyRepository = occupancyRepository;
        this.clueRepository = clueRepository;
        this.clueAclRepository = clueAclRepository;
        this.roundTurnService = roundTurnService;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    public SessionViewResponse getSession(UUID sessionId, UUID deviceId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));
        Scenario scenario = scenarioRepository.findById(session.getScenarioId())
            .orElseThrow(() -> new SessionNotFoundException("scenario not found: " + session.getScenarioId()));

        int required = scenario.characters().size();
        List<PlayerSummary> players = session.getPlayers().stream()
            .map(p -> new PlayerSummary(p.getId().toString(), p.getNickname(), p.isHost()))
            .toList();

        List<SessionViewResponse.ScenarioLocationView> locationViews = scenario.locations() == null ? List.of()
            : scenario.locations().stream()
                .map(l -> new SessionViewResponse.ScenarioLocationView(l.id(), l.name(), l.icon()))
                .toList();

        SessionViewResponse.RoundView roundView = null;
        SessionViewResponse.TurnView currentTurnView = null;
        List<SessionViewResponse.OccupancyView> occupancyViews = List.of();

        Integer roundNumber = session.getCurrentRoundNumber();
        if (roundNumber != null) {
            roundView = roundRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber)
                .map(r -> new SessionViewResponse.RoundView(
                    r.getRoundNumber(), r.getPrompt(), r.getCommonHint(),
                    r.getStartedAt().toEpochMilli(), r.getDeadlineAt().toEpochMilli()
                ))
                .orElse(null);

            if ("round".equals(session.getState())) {
                List<LocationOccupancy> taken = occupancyRepository.findBySessionIdAndRoundNumber(sessionId, roundNumber);
                occupancyViews = taken.stream()
                    .map(o -> new SessionViewResponse.OccupancyView(
                        o.getLocationId(), o.getPlayerId().toString(), o.getCharacterId(), o.isAutoSelected()))
                    .toList();

                List<String> turnOrder = session.getTurnOrder();
                int turnIndex = taken.size(); // next turn = number already completed
                if (turnOrder != null && !turnOrder.isEmpty() && turnIndex < turnOrder.size()) {
                    String characterId = TurnQueueCalculator.characterIdAt(turnOrder, roundNumber, turnIndex);
                    Player currentTurnPlayer = session.getPlayers().stream()
                        .filter(p -> characterId.equals(p.getAssignedCharacterId()))
                        .findFirst().orElse(null);

                    if (currentTurnPlayer != null) {
                        List<String> occupiedLocations = taken.stream().map(LocationOccupancy::getLocationId).toList();
                        List<String> candidates = new ArrayList<>(scenario.locationPool());
                        candidates.removeAll(occupiedLocations);

                        long deadlineAt = roundTurnService.getTurnDeadline(sessionId, roundNumber, turnIndex)
                            .map(i -> i.toEpochMilli())
                            .orElse(0L);

                        currentTurnView = new SessionViewResponse.TurnView(
                            turnIndex, currentTurnPlayer.getId().toString(), characterId, deadlineAt, candidates);
                    }
                }
            }
        }

        SessionViewResponse.MeView meView = null;
        if (deviceId != null) {
            Player me = playerRepository.findBySessionIdAndDeviceId(sessionId, deviceId).orElse(null);
            if (me != null) {
                boolean cardVisible = CARD_VISIBLE_STATES.contains(session.getState());
                SessionViewResponse.CharacterCardView characterView = cardVisible
                    ? buildCharacterCardView(scenario, me.getAssignedCharacterId(), session.getTurnOrder())
                    : null;

                SessionViewResponse.ObjectiveView objectiveView = null;
                if (cardVisible && roundNumber != null && me.getAssignedCharacterId() != null) {
                    String obj = ObjectiveResolver.resolve(
                        scenario, me.getAssignedCharacterId(), roundNumber
                    ).orElse(null);
                    if (obj != null) {
                        objectiveView = new SessionViewResponse.ObjectiveView(
                            roundNumber, scenario.roundCount(), obj
                        );
                    }
                }

                List<SessionViewResponse.ClueView> myClues = List.of();
                if (cardVisible) {
                    List<ClueAcl> acls = clueAclRepository.findBySessionIdAndPlayerId(sessionId, me.getId());
                    if (!acls.isEmpty()) {
                        List<UUID> clueIds = acls.stream().map(ClueAcl::getClueId).toList();
                        Map<UUID, Clue> cluesById = clueRepository.findAllById(clueIds).stream()
                            .collect(Collectors.toMap(Clue::getId, c -> c));
                        myClues = acls.stream()
                            .map(acl -> {
                                Clue c = cluesById.get(acl.getClueId());
                                if (c == null) return null;
                                return new SessionViewResponse.ClueView(
                                    c.getId().toString(), c.getItemId(), c.getTitle(),
                                    c.getOriginLocationId(), c.getRoundNumberDiscovered(),
                                    c.getDiscoveredAt().toEpochMilli(), acl.getSource());
                            })
                            .filter(java.util.Objects::nonNull)
                            .toList();
                    }
                }

                List<SessionViewResponse.OwnedClueView> allOwnedClues = List.of();
                if ("round".equals(session.getState())) {
                    allOwnedClues = clueRepository.findBySessionId(sessionId).stream()
                        .map(c -> new SessionViewResponse.OwnedClueView(
                            c.getId().toString(), c.getItemId(), c.getTitle(),
                            c.getCurrentOwnerPlayerId().toString(), c.getRoundNumberDiscovered()))
                        .toList();
                }

                String assignedCharacterId = cardVisible ? me.getAssignedCharacterId() : null;
                Long tutorialAckedAtMs = me.getTutorialAckedAt() != null
                    ? me.getTutorialAckedAt().toEpochMilli() : null;
                meView = new SessionViewResponse.MeView(
                    me.getId().toString(), me.getNickname(), me.isHost(),
                    assignedCharacterId, characterView, objectiveView, tutorialAckedAtMs,
                    myClues, allOwnedClues
                );
            }
        }

        return new SessionViewResponse(
            session.getId().toString(), session.getInviteCode(), session.getScenarioId(),
            session.getPhase(), required, players.size(), players,
            session.getState(), session.getCurrentRoundNumber(), session.getTurnOrder(),
            locationViews, roundView, currentTurnView, occupancyViews, meView
        );
    }

    private SessionViewResponse.CharacterCardView buildCharacterCardView(
            Scenario scenario, String characterId, List<String> turnOrder) {
        if (characterId == null || scenario == null || scenario.characters() == null) return null;

        ScenarioCharacter ch = scenario.characters().stream()
            .filter(c -> characterId.equals(c.id()))
            .findFirst()
            .orElse(null);
        if (ch == null) return null;

        int turnOrderIndex = (turnOrder != null) ? turnOrder.indexOf(characterId) : -1;

        SessionViewResponse.LocationRef alibiLocation = null;
        if (ch.alibiLocationId() != null && scenario.locations() != null) {
            alibiLocation = scenario.locations().stream()
                .filter(l -> ch.alibiLocationId().equals(l.id()))
                .findFirst()
                .map(l -> new SessionViewResponse.LocationRef(l.id(), l.name(), l.icon()))
                .orElse(null);
        }

        return new SessionViewResponse.CharacterCardView(
            characterId, ch.name(), turnOrderIndex,
            ch.speechStyle(), ch.background(), ch.motive(),
            ch.alibi(), ch.secret(), ch.relationships(),
            alibiLocation, List.of()
        );
    }

    public CreateSessionResponse createSession(String scenarioId, String hostNickname, UUID deviceId) {
        scenarioRepository.findById(scenarioId)
            .orElseThrow(() -> new IllegalArgumentException("unknown scenario: " + scenarioId));

        String trimmed = Nicknames.validate(hostNickname);

        for (int attempt = 0; attempt < INVITE_CODE_RETRY_LIMIT; attempt++) {
            String code = inviteCodeGenerator.next();
            try {
                return transactionTemplate.execute(status -> {
                    Session session = new Session(code, scenarioId);
                    Player host = new Player(trimmed, true, deviceId);
                    session.addPlayer(host);
                    sessionRepository.saveAndFlush(session);
                    return new CreateSessionResponse(
                        session.getId().toString(),
                        session.getInviteCode(),
                        session.getScenarioId(),
                        host.getNickname(),
                        session.getPhase(),
                        host.getId().toString()
                    );
                });
            } catch (DataIntegrityViolationException dup) {
                log.debug("invite code collision on attempt {}: {}", attempt + 1, code);
            }
        }

        log.error("invite code exhausted after {} attempts", INVITE_CODE_RETRY_LIMIT);
        throw new IllegalStateException("invite code exhausted");
    }

}
