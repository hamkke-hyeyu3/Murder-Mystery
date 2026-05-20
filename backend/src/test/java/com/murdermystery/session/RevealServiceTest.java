package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioCharacter.Mission;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.CulpritRevealStartedPayload;
import com.murdermystery.ws.event.MissionPhaseStartedPayload;
import com.murdermystery.ws.event.MissionRevealedPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RevealServiceTest {

    private SessionRepository sessionRepo;
    private ScenarioRepository scenarioRepo;
    private SessionEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private ScheduledExecutorService scheduler;
    private RevealService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final String INVITE_CODE = "RVLAB1";
    private static final String CHAR_ALICE = "alice";
    private static final String CHAR_BOB = "bob";
    private static final String CHAR_CHARLIE = "charlie";

    private static final List<Mission> ALICE_MISSIONS = List.of(
        new Mission("진실 보호", "유언장 내용을 공개하지 않는다."),
        new Mission("진범 특정", "집사를 지목에서 활용한다.")
    );
    private static final List<Mission> BOB_MISSIONS = List.of(
        new Mission("의심 분산", "찰리에게 의심이 집중되도록 유도한다.")
    );

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        eventPublisher = mock(SessionEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new RevealService(sessionRepo, scenarioRepo, eventPublisher, txTemplate, scheduler);
        service.revealDurationMs = 50; // fast for tests
    }

    @AfterEach
    void tearDown() {
        service.cancelPendingForTest();
        scheduler.shutdownNow();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Session voteSession(String outcome, String winnerId) {
        Session s = new Session(INVITE_CODE, "dev-duo");
        s.setPhase("in_progress");
        s.setState("vote");
        s.setVoteOutcome(outcome);
        s.setVoteWinnerCharacterId(winnerId);

        Player alice = new Player("alice-nick", true, UUID.randomUUID());
        Player bob = new Player("bob-nick", false, UUID.randomUUID());
        setId(alice, ALICE_ID);
        setId(bob, BOB_ID);
        alice.setAssignedCharacterId(CHAR_ALICE);
        bob.setAssignedCharacterId(CHAR_BOB);
        s.addPlayer(alice);
        s.addPlayer(bob);
        return s;
    }

    private Scenario scenario() {
        ScenarioCharacter alice = new ScenarioCharacter(CHAR_ALICE, "앨리스", null, null, null,
            null, null, null, null, null, ALICE_MISSIONS);
        ScenarioCharacter bob = new ScenarioCharacter(CHAR_BOB, "밥", null, null, null,
            null, null, null, null, null, BOB_MISSIONS);
        ScenarioCharacter charlie = new ScenarioCharacter(CHAR_CHARLIE, "찰리", null, null, null,
            null, null, null, null, null, null);
        return mock(Scenario.class, inv -> switch (inv.getMethod().getName()) {
            case "characters" -> List.of(alice, bob, charlie);
            case "trueCulpritCharacterId" -> CHAR_CHARLIE;
            default -> null;
        });
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

    // ── startReveal() ───────────────────────────────────────────────────────

    @Test
    void startReveal_singleWinner_setsStateRevealAndBroadcasts() {
        Session session = voteSession("single_winner", CHAR_BOB);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        service.startReveal(SESSION_ID);

        assertThat(session.getState()).isEqualTo("reveal");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(2)).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), payloadCaptor.capture());

        assertThat(typeCaptor.getAllValues()).containsExactly("SESSION_STATE_CHANGED", "CULPRIT_REVEAL_STARTED");
        CulpritRevealStartedPayload reveal = (CulpritRevealStartedPayload) payloadCaptor.getAllValues().get(1);
        assertThat(reveal.outcome()).isEqualTo("single_winner");
        assertThat(reveal.culpritCharacterId()).isEqualTo(CHAR_BOB);
        assertThat(reveal.accusedCharacterId()).isEqualTo(CHAR_BOB);
    }

    @Test
    void startReveal_failed_culpritIsTrueCulpritFromScenario() {
        Session session = voteSession("failed", null);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        service.startReveal(SESSION_ID);

        assertThat(session.getState()).isEqualTo("reveal");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(any(), any(), payloadCaptor.capture());

        CulpritRevealStartedPayload reveal = (CulpritRevealStartedPayload) payloadCaptor.getAllValues().get(1);
        assertThat(reveal.outcome()).isEqualTo("failed");
        assertThat(reveal.culpritCharacterId()).isEqualTo(CHAR_CHARLIE); // true culprit from scenario
        assertThat(reveal.accusedCharacterId()).isNull();
    }

    @Test
    void startReveal_isIdempotent_secondCallNoOp() {
        Session session = voteSession("single_winner", CHAR_BOB);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        service.startReveal(SESSION_ID);
        service.startReveal(SESSION_ID); // second call must be no-op

        verify(sessionRepo, times(1)).findByIdForUpdate(SESSION_ID); // only 1 TX load
    }

    @Test
    void startReveal_rejectsIfStateNotVote() {
        Session session = voteSession("single_winner", CHAR_BOB);
        session.setState("round"); // wrong state
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startReveal(SESSION_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startReveal_rejectsIfVoteOutcomeNull() {
        Session session = voteSession(null, null); // tie still in progress
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startReveal(SESSION_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startReveal_schedulesMissionPhaseAfterDelay() {
        ScheduledExecutorService mockScheduler = mock(ScheduledExecutorService.class);
        when(mockScheduler.schedule(any(Runnable.class), anyLong(), any())).thenReturn(mock(java.util.concurrent.ScheduledFuture.class));

        RevealService svc = new RevealService(sessionRepo, scenarioRepo, eventPublisher, txTemplate, mockScheduler);
        svc.revealDurationMs = 5000L;

        Session session = voteSession("single_winner", CHAR_BOB);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        svc.startReveal(SESSION_ID);

        verify(mockScheduler).schedule(any(Runnable.class), eq(5000L), eq(java.util.concurrent.TimeUnit.MILLISECONDS));

        svc.cancelPendingForTest();
        mockScheduler.shutdownNow();
    }

    // ── startMissionPhase() ─────────────────────────────────────────────────

    @Test
    void startMissionPhase_setsStateMissionAndBroadcasts() {
        Session session = voteSession("single_winner", CHAR_BOB);
        session.setState("reveal"); // simulate we're already in reveal
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        service.startMissionPhase(SESSION_ID, INVITE_CODE);

        assertThat(session.getState()).isEqualTo("mission");

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, atLeast(2)).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        assertThat(typeCaptor.getAllValues()).contains("SESSION_STATE_CHANGED", "MISSION_PHASE_STARTED");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(eq(SESSION_ID.toString()), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues().get(0)).isInstanceOf(SessionStateChangedPayload.class);
        assertThat(((SessionStateChangedPayload) payloadCaptor.getAllValues().get(0)).state()).isEqualTo("mission");
        assertThat(payloadCaptor.getAllValues().get(1)).isInstanceOf(MissionPhaseStartedPayload.class);
    }

    @Test
    void startMissionPhase_sendsPrivateMissionRevealedPerPlayerWithTheirMissions() {
        Session session = voteSession("single_winner", CHAR_BOB);
        session.setState("reveal");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("dev-duo")).thenReturn(Optional.of(scenario()));

        service.startMissionPhase(SESSION_ID, INVITE_CODE);

        // Each player gets exactly their character's missions — no cross-leakage
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> playerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(2)).publishToPlayer(
            eq(INVITE_CODE), playerIdCaptor.capture(), eq(SESSION_ID.toString()),
            eq("MISSION_REVEALED"), payloadCaptor.capture());

        List<String> playerIds = playerIdCaptor.getAllValues();
        List<Object> payloads = payloadCaptor.getAllValues();

        // Find Alice's mission payload
        int aliceIdx = playerIds.indexOf(ALICE_ID.toString());
        int bobIdx = playerIds.indexOf(BOB_ID.toString());
        assertThat(aliceIdx).isGreaterThanOrEqualTo(0);
        assertThat(bobIdx).isGreaterThanOrEqualTo(0);

        MissionRevealedPayload alicePayload = (MissionRevealedPayload) payloads.get(aliceIdx);
        MissionRevealedPayload bobPayload = (MissionRevealedPayload) payloads.get(bobIdx);

        assertThat(alicePayload.missions()).isEqualTo(ALICE_MISSIONS);
        assertThat(bobPayload.missions()).isEqualTo(BOB_MISSIONS);
        // Ensure Alice did not receive Bob's mission
        assertThat(alicePayload.missions()).doesNotContainAnyElementsOf(BOB_MISSIONS);
    }

    @Test
    void startMissionPhase_staleGuard_skipsIfStateNoLongerReveal() {
        Session session = voteSession("single_winner", CHAR_BOB);
        session.setState("mission"); // already past reveal
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startMissionPhase(SESSION_ID, INVITE_CODE);

        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), any(), any());
    }
}
