package com.murdermystery.session;

import com.murdermystery.scenario.Round;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioItem;
import com.murdermystery.scenario.ScenarioLocation;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CluePayload;
import com.murdermystery.ws.event.LocationSelectedPayload;
import com.murdermystery.ws.event.ServerTimeSyncPayload;
import com.murdermystery.ws.event.TurnStartedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoundTurnServiceTest {

    private SessionRepository sessionRepo;
    private RoundRepository roundRepo;
    private ScenarioRepository scenarioRepo;
    private LocationOccupancyRepository occupancyRepo;
    private ClueRepository clueRepo;
    private ClueAclRepository clueAclRepo;
    private PlayerRepository playerRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private ScheduledExecutorService scheduler;
    private Clock clock;
    private RoundTurnService service;

    private final List<Runnable> capturedTasks = new ArrayList<>();

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID CHARLIE_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        roundRepo = mock(RoundRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        occupancyRepo = mock(LocationOccupancyRepository.class);
        clueRepo = mock(ClueRepository.class);
        clueAclRepo = mock(ClueAclRepository.class);
        playerRepo = mock(PlayerRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        scheduler = mock(ScheduledExecutorService.class);
        capturedTasks.clear();

        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

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

        service = new RoundTurnService(
            sessionRepo, roundRepo, scenarioRepo, occupancyRepo, clueRepo, clueAclRepo, playerRepo,
            txTemplate, eventPublisher, scheduler, new Random(0), clock
        );
    }

    private Session roundSession(List<String> turnOrder, int roundNumber) {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setTurnOrder(turnOrder);
        s.setCurrentRoundNumber(roundNumber);

        Player alice = new Player("alice", true, UUID.randomUUID());
        Player bob = new Player("bob", false, UUID.randomUUID());
        Player charlie = new Player("charlie", false, UUID.randomUUID());
        setId(alice, ALICE_ID);
        setId(bob, BOB_ID);
        setId(charlie, CHARLIE_ID);
        alice.setAssignedCharacterId("char-a");
        bob.setAssignedCharacterId("char-b");
        charlie.setAssignedCharacterId("char-c");
        s.addPlayer(alice);
        s.addPlayer(bob);
        s.addPlayer(charlie);
        return s;
    }

    private void setId(Player p, UUID id) {
        try {
            var f = Player.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void stubRoundExists(int roundNumber) {
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, roundNumber))
            .thenReturn(Optional.of(new RoundEntity(SESSION_ID, roundNumber, null, null,
                FIXED_NOW, FIXED_NOW.plusSeconds(600))));
    }

    private Scenario toy3Scenario() {
        return new Scenario(
            "toy-manor", "Toy Manor", null, null, 60,
            List.of(
                new ScenarioCharacter("char-a", "Alice", null, null, null, null, null, null, null, null),
                new ScenarioCharacter("char-b", "Bob", null, null, null, null, null, null, null, null),
                new ScenarioCharacter("char-c", "Charlie", null, null, null, null, null, null, null, null)
            ),
            List.of(
                new ScenarioLocation("library", "도서관", "📚", null),
                new ScenarioLocation("kitchen", "주방", "🍳", null),
                new ScenarioLocation("garden", "정원", "🌿", null)
            ),
            List.of("library", "kitchen", "garden"),
            List.of(
                new ScenarioItem("torn-letter", "찢어진 편지", "library"),
                new ScenarioItem("poison-vial", "독약 병", "kitchen"),
                new ScenarioItem("muddy-glove", "진흙 장갑", "garden")
            ),
            "char-c", true, 2,
            List.of(
                new Round(null, null, 600),
                new Round("단서를 공유하세요.", "공통 힌트", 600)
            )
        );
    }

    // ── startRoundTurns ──

    @Test
    void startRoundTurns_round1_emitsTurnStartedForFirstCharacter() {
        List<String> order = List.of("char-a", "char-b", "char-c");
        Session session = roundSession(order, 1);
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of());
        when(playerRepo.findAll()).thenReturn(session.getPlayers());

        service.startRoundTurns(SESSION_ID, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TurnStartedPayload> captor = ArgumentCaptor.forClass(TurnStartedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("TURN_STARTED"), captor.capture());

        TurnStartedPayload payload = captor.getValue();
        assertThat(payload.roundNumber()).isEqualTo(1);
        assertThat(payload.turnIndex()).isEqualTo(0);
        assertThat(payload.characterId()).isEqualTo("char-a");
        assertThat(payload.candidateLocationIds()).containsExactlyInAnyOrder("library", "kitchen", "garden");
        assertThat(payload.deadlineAt()).isEqualTo(FIXED_NOW.plusSeconds(30).toEpochMilli());
    }

    @Test
    void startRoundTurns_round2_appliesSnakeRotation() {
        // round 2: starts at char-b (index ((2-1)+0) mod 3 = 1)
        List<String> order = List.of("char-a", "char-b", "char-c");
        Session session = roundSession(order, 2);
        stubRoundExists(2);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 2)).thenReturn(List.of());
        when(playerRepo.findAll()).thenReturn(session.getPlayers());

        service.startRoundTurns(SESSION_ID, 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TurnStartedPayload> captor = ArgumentCaptor.forClass(TurnStartedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("TURN_STARTED"), captor.capture());
        assertThat(captor.getValue().characterId()).isEqualTo("char-b");
        assertThat(captor.getValue().turnIndex()).isEqualTo(0);
    }

    @Test
    void startRoundTurns_schedulesAutoSelectAt30s() {
        Session session = roundSession(List.of("char-a", "char-b", "char-c"), 1);
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of());
        when(playerRepo.findAll()).thenReturn(session.getPlayers());

        service.startRoundTurns(SESSION_ID, 1);

        verify(scheduler).schedule(any(Runnable.class), eq(30L), eq(TimeUnit.SECONDS));
        assertThat(capturedTasks).hasSize(1);
    }

    @Test
    void startRoundTurns_publishesServerTimeSyncBeforeTurnStarted() {
        Session session = roundSession(List.of("char-a", "char-b", "char-c"), 1);
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of());
        when(playerRepo.findAll()).thenReturn(session.getPlayers());

        service.startRoundTurns(SESSION_ID, 1);

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("SERVER_TIME_SYNC"), any(ServerTimeSyncPayload.class));
        order.verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("TURN_STARTED"), any(TurnStartedPayload.class));
    }

    @Test
    void startRoundTurns_skipsWhenSessionNotInRoundState() {
        Session session = roundSession(List.of("char-a", "char-b", "char-c"), 1);
        session.setState("tutorial");
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startRoundTurns(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startRoundTurns_skipsWhenRoundsRowMissing() {
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(Optional.empty());

        service.startRoundTurns(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── selectLocation ──

    private Session roundSessionForSelect(int roundNumber) {
        List<String> order = List.of("char-a", "char-b", "char-c");
        Session s = roundSession(order, roundNumber);
        return s;
    }

    private void stubSelectHappyPath(Session session, int roundNumber) {
        stubRoundExists(roundNumber);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, roundNumber)).thenReturn(List.of());
    }

    @Test
    void selectLocation_happyPath_broadcastsLocationSelectedAndCancelsTimeout() {
        Session session = roundSessionForSelect(1);
        stubSelectHappyPath(session, 1);
        // prime turnDeadlines so grace check passes
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> mockFuture = mock(ScheduledFuture.class);
        // simulate a pending future already registered
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("LOCATION_SELECTED"), any(LocationSelectedPayload.class));
        verify(occupancyRepo).save(any(LocationOccupancy.class));
    }

    @Test
    void selectLocation_deliversClueToSelectingPlayer() {
        Session session = roundSessionForSelect(1);
        stubSelectHappyPath(session, 1);
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        ArgumentCaptor<CluePayload> captor = ArgumentCaptor.forClass(CluePayload.class);
        verify(eventPublisher).publishToPlayer(
            eq("ABCDEF"), eq(ALICE_ID.toString()), eq(SESSION_ID.toString()), eq("CLUE_DELIVERED"), captor.capture());
        assertThat(captor.getValue().itemId()).isEqualTo("torn-letter");
        assertThat(captor.getValue().originLocationId()).isEqualTo("library");
    }

    @Test
    void selectLocation_rejectedWhenNotMyTurn() {
        Session session = roundSessionForSelect(1);
        stubSelectHappyPath(session, 1);
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        // BOB tries to select on ALICE's turn (turnIndex=0)
        service.selectLocation(SESSION_ID, BOB_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher, never()).publish(any(), eq("LOCATION_SELECTED"), any());
    }

    @Test
    void selectLocation_rejectedWhenLocationAlreadyOccupied() {
        Session session = roundSessionForSelect(1);
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        LocationOccupancy taken = new LocationOccupancy(SESSION_ID, 1, "library", BOB_ID, "char-b", FIXED_NOW, false);
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of(taken));
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher, never()).publish(any(), eq("LOCATION_SELECTED"), any());
    }

    @Test
    void selectLocation_rejectedAfterGracePeriod() {
        // deadline was 30s ago — grace period (1s) has fully elapsed
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.minusSeconds(2));

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher, never()).publish(any(), eq("LOCATION_SELECTED"), any());
        verify(sessionRepo, never()).findByIdForUpdate(any());
    }

    @Test
    void selectLocation_acceptedWithinGracePeriod() {
        Session session = roundSessionForSelect(1);
        stubSelectHappyPath(session, 1);
        // deadline exactly at FIXED_NOW: grace = FIXED_NOW + 1s; clock.instant() = FIXED_NOW → accepted
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW);

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("LOCATION_SELECTED"), any());
    }

    @Test
    void selectLocation_rejectedOnStaleTurnIndex() {
        Session session = roundSessionForSelect(1);
        stubSelectHappyPath(session, 1);
        service.putDeadlineForTest(SESSION_ID, 1, 5, FIXED_NOW.plusSeconds(30)); // index 5 >= n(3)

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 5);

        verify(eventPublisher, never()).publish(any(), eq("LOCATION_SELECTED"), any());
    }

    @Test
    void selectLocation_emitsRoundTurnsCompleteAfterLastTurn() {
        // 1-player scenario for simplicity: only char-a in turn order
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setTurnOrder(List.of("char-a"));
        s.setCurrentRoundNumber(1);
        Player alice = new Player("alice", true, UUID.randomUUID());
        setId(alice, ALICE_ID);
        alice.setAssignedCharacterId("char-a");
        s.addPlayer(alice);

        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of());
        service.putDeadlineForTest(SESSION_ID, 1, 0, FIXED_NOW.plusSeconds(30));

        service.selectLocation(SESSION_ID, ALICE_ID, "ABCDEF", "library", 1, 0);

        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ROUND_TURNS_COMPLETE"), any());
    }

    @Test
    void startRoundTurns_candidateLocationsExcludeAlreadyOccupied() {
        // library is already taken — only kitchen and garden should be candidates
        List<String> order = List.of("char-a", "char-b", "char-c");
        Session session = roundSession(order, 1);
        stubRoundExists(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(toy3Scenario()));
        LocationOccupancy taken = new LocationOccupancy(
            SESSION_ID, 1, "library", ALICE_ID, "char-a", FIXED_NOW, false);
        when(occupancyRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(List.of(taken));
        when(playerRepo.findAll()).thenReturn(session.getPlayers());

        service.startRoundTurns(SESSION_ID, 1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<TurnStartedPayload> captor = ArgumentCaptor.forClass(TurnStartedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("TURN_STARTED"), captor.capture());
        assertThat(captor.getValue().candidateLocationIds()).containsExactlyInAnyOrder("kitchen", "garden");
    }
}
