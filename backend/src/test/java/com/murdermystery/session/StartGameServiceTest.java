package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CharacterCardDealtPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StartGameServiceTest {

    private SessionRepository sessionRepo;
    private ScenarioRepository scenarioRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private ScheduledExecutorService scheduler;
    private StartGameService service;

    // Captures the Runnable passed to scheduler.schedule so tests can trigger it manually
    private final List<Runnable> capturedTasks = new ArrayList<>();

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID HOST_DEVICE = UUID.randomUUID();
    private static final UUID GUEST_DEVICE = UUID.randomUUID();
    private static final UUID OTHER_DEVICE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        scheduler = mock(ScheduledExecutorService.class);
        capturedTasks.clear();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        //noinspection unchecked
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(inv -> {
                capturedTasks.add(inv.getArgument(0, Runnable.class));
                return mock(ScheduledFuture.class);
            });

        Scenario scenario = mock(Scenario.class);
        when(scenario.characters()).thenReturn(List.of(
            new ScenarioCharacter("char-a", "Alice"),
            new ScenarioCharacter("char-b", "Bob"),
            new ScenarioCharacter("char-c", "Charlie")
        ));
        when(scenarioRepo.findById(any())).thenReturn(Optional.of(scenario));
        when(scenarioRepo.findAll()).thenReturn(List.of(scenario));

        // deterministic random (seed=0)
        service = new StartGameService(
            sessionRepo, scenarioRepo, txTemplate, eventPublisher, scheduler, new Random(0), 5000);
    }

    private Session lobbySessionWith3Players() {
        Session s = new Session("123456", "toy-manor");
        s.addPlayer(new Player("alice", true, HOST_DEVICE));
        s.addPlayer(new Player("bob", false, GUEST_DEVICE));
        s.addPlayer(new Player("charlie", false, OTHER_DEVICE));
        return s;
    }

    @Test
    void start_unknownSession_throwsSessionNotFound() {
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void start_phaseInProgress_throwsSessionAlreadyStarted() {
        Session s = lobbySessionWith3Players();
        s.setPhase("in_progress");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(SessionAlreadyStartedException.class);
    }

    @Test
    void start_phaseEnded_throwsSessionAlreadyStarted() {
        Session s = lobbySessionWith3Players();
        s.setPhase("ended");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(SessionAlreadyStartedException.class);
    }

    @Test
    void start_byNonHostDevice_throwsNotHost() {
        Session s = lobbySessionWith3Players();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, GUEST_DEVICE))
            .isInstanceOf(NotHostException.class);
    }

    @Test
    void start_byHostWithNullDeviceId_throwsNotHost() {
        Session s = new Session("123456", "toy-manor");
        s.addPlayer(new Player("alice", true)); // no deviceId
        s.addPlayer(new Player("bob", false, GUEST_DEVICE));
        s.addPlayer(new Player("charlie", false, OTHER_DEVICE));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(NotHostException.class);
    }

    @Test
    void start_joinedLessThanRequired_throwsLobbyCountMismatch() {
        Session s = new Session("123456", "toy-manor");
        s.addPlayer(new Player("alice", true, HOST_DEVICE));
        s.addPlayer(new Player("bob", false, GUEST_DEVICE));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(LobbyCountMismatchException.class)
            .satisfies(e -> {
                LobbyCountMismatchException ex = (LobbyCountMismatchException) e;
                assertThat(ex.getJoined()).isEqualTo(2);
                assertThat(ex.getRequired()).isEqualTo(3);
            });
    }

    @Test
    void start_joinedMoreThanRequired_throwsLobbyCountMismatch() {
        Session s = lobbySessionWith3Players();
        s.addPlayer(new Player("dave", false, UUID.randomUUID()));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(LobbyCountMismatchException.class)
            .satisfies(e -> {
                LobbyCountMismatchException ex = (LobbyCountMismatchException) e;
                assertThat(ex.getJoined()).isEqualTo(4);
                assertThat(ex.getRequired()).isEqualTo(3);
            });
    }

    @Test
    void start_happyPath_setsPhaseStateTurnOrderAndAssignsCharacters() {
        Session s = lobbySessionWith3Players();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        StartGameResponse res = service.start(SESSION_ID, HOST_DEVICE);

        assertThat(s.getPhase()).isEqualTo("in_progress");
        assertThat(s.getState()).isEqualTo("intro");
        assertThat(s.getTurnOrder()).hasSize(3).doesNotHaveDuplicates();
        assertThat(res.phase()).isEqualTo("in_progress");
        assertThat(res.state()).isEqualTo("intro");
        assertThat(res.turnOrder()).containsExactlyElementsOf(s.getTurnOrder());

        // Every player got a character assigned
        assertThat(s.getPlayers()).allSatisfy(
            p -> assertThat(p.getAssignedCharacterId()).isNotNull()
        );
        // No two players share the same character
        long distinctChars = s.getPlayers().stream()
            .map(Player::getAssignedCharacterId).distinct().count();
        assertThat(distinctChars).isEqualTo(3);
    }

    @Test
    void start_onSuccess_broadcastsSessionStateChangedAfterCommit() {
        Session s = lobbySessionWith3Players();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.start(SESSION_ID, HOST_DEVICE);

        InOrder order = inOrder(sessionRepo, eventPublisher);
        order.verify(sessionRepo).saveAndFlush(any());
        order.verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }

    @Test
    void start_onSuccess_broadcastPayloadContainsTurnOrder() {
        Session s = lobbySessionWith3Players();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.start(SESSION_ID, HOST_DEVICE);

        ArgumentCaptor<SessionStateChangedPayload> payloadCaptor =
            ArgumentCaptor.forClass(SessionStateChangedPayload.class);
        verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().state()).isEqualTo("intro");
        assertThat(payloadCaptor.getValue().turnOrder()).hasSize(3);
    }

    @Test
    void start_failedValidation_doesNotBroadcast() {
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(SESSION_ID, HOST_DEVICE))
            .isInstanceOf(SessionNotFoundException.class);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void start_onSuccess_schedulesCharacterAssignmentTransition() {
        Session s = lobbySessionWith3Players();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.start(SESSION_ID, HOST_DEVICE);

        verify(scheduler).schedule(any(Runnable.class), eq(5000L), eq(TimeUnit.MILLISECONDS));
        assertThat(capturedTasks).hasSize(1);
    }

    @Test
    void transition_happyPath_updatesStateAndPublishesPrivatePerPlayer() {
        Session s = lobbySessionWith3Players();
        s.setPhase("in_progress");
        s.setState("intro");
        // Simulate prior start: assign characters and turn order
        List<String> turnOrder = List.of("char-a", "char-b", "char-c");
        s.setTurnOrder(turnOrder);
        List<Player> players = s.getPlayers().stream()
            .sorted(java.util.Comparator.comparing(Player::getJoinedAt))
            .toList();
        players.get(0).setAssignedCharacterId("char-a");
        players.get(1).setAssignedCharacterId("char-b");
        players.get(2).setAssignedCharacterId("char-c");

        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionToCharacterAssignment(SESSION_ID, "123456");

        assertThat(s.getState()).isEqualTo("character_assignment");
        verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), any());
        verify(eventPublisher, times(3)).publishToPlayer(
            eq("123456"), any(), any(), eq("CHARACTER_CARD_DEALT"), any());
    }

    @Test
    void transition_whenPhaseNotInProgress_silentlyDrops() {
        Session s = lobbySessionWith3Players();
        s.setPhase("ended");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.transitionToCharacterAssignment(SESSION_ID, "123456");

        verify(sessionRepo, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), any(), any());
    }

    @Test
    void transition_whenStateAlreadyAdvanced_silentlyDrops() {
        Session s = lobbySessionWith3Players();
        s.setPhase("in_progress");
        s.setState("tutorial"); // already past intro
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.transitionToCharacterAssignment(SESSION_ID, "123456");

        verify(sessionRepo, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void transition_cardPayload_containsCorrectCharacterName() {
        Session s = lobbySessionWith3Players();
        s.setPhase("in_progress");
        s.setState("intro");
        s.setTurnOrder(List.of("char-a", "char-b", "char-c"));
        List<Player> players = s.getPlayers().stream()
            .sorted(java.util.Comparator.comparing(Player::getJoinedAt))
            .toList();
        players.get(0).setAssignedCharacterId("char-a");
        players.get(1).setAssignedCharacterId("char-b");
        players.get(2).setAssignedCharacterId("char-c");

        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionToCharacterAssignment(SESSION_ID, "123456");

        ArgumentCaptor<CharacterCardDealtPayload> captor =
            ArgumentCaptor.forClass(CharacterCardDealtPayload.class);
        verify(eventPublisher, times(3)).publishToPlayer(
            any(), any(), any(), eq("CHARACTER_CARD_DEALT"), captor.capture());

        List<CharacterCardDealtPayload> payloads = captor.getAllValues();
        assertThat(payloads).anySatisfy(p -> {
            assertThat(p.characterId()).isEqualTo("char-a");
            assertThat(p.name()).isEqualTo("Alice");
            assertThat(p.turnOrderIndex()).isEqualTo(0);
        });
        assertThat(payloads).anySatisfy(p -> {
            assertThat(p.characterId()).isEqualTo("char-b");
            assertThat(p.name()).isEqualTo("Bob");
            assertThat(p.turnOrderIndex()).isEqualTo(1);
        });
    }
}
