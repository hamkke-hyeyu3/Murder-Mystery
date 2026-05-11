package com.murdermystery.session;

import com.murdermystery.scenario.Round;
import com.murdermystery.scenario.RoundObjective;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.ObjectiveUpdatedPayload;
import com.murdermystery.ws.event.RoundStartedPayload;
import com.murdermystery.ws.event.ServerTimeSyncPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoundServiceTest {

    private SessionRepository sessionRepo;
    private RoundRepository roundRepo;
    private ScenarioRepository scenarioRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private RoundTurnService roundTurnService;
    private RoundService service;

    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        roundRepo = mock(RoundRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        roundTurnService = mock(RoundTurnService.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        service = new RoundService(sessionRepo, roundRepo, scenarioRepo, txTemplate, eventPublisher, roundTurnService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Session roundSession() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        return s;
    }

    private Scenario scenarioWith(List<Round> rounds) {
        Scenario s = mock(Scenario.class);
        when(s.id()).thenReturn("toy-manor");
        when(s.roundCount()).thenReturn(rounds.size());
        when(s.rounds()).thenReturn(rounds);
        return s;
    }

    private Round round(String prompt, String commonHint, int timeLimitSec) {
        return new Round(prompt, commonHint, timeLimitSec);
    }

    // ── startRound: event order ───────────────────────────────────────────────

    @Test
    void startRound_emitsServerTimeSyncThenRoundStarted_inOrder() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publish(any(), eq("SERVER_TIME_SYNC"), any(ServerTimeSyncPayload.class));
        order.verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), any(RoundStartedPayload.class));
    }

    // ── startRound: k=1 fallback ──────────────────────────────────────────────

    @Test
    void startRound_round1_promptBlank_usesDefaultIntroTrigger() {
        Scenario scenario = scenarioWith(List.of(round("", null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().prompt()).isEqualTo(RoundService.DEFAULT_K1_INTRO_PROMPT);
    }

    @Test
    void startRound_round1_promptNull_usesDefaultIntroTrigger() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().prompt()).isEqualTo(RoundService.DEFAULT_K1_INTRO_PROMPT);
    }

    @Test
    void startRound_round1_promptPresent_keepsAuthorPrompt() {
        Scenario scenario = scenarioWith(List.of(round("작가가 쓴 첫 라운드 프롬프트.", null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().prompt()).isEqualTo("작가가 쓴 첫 라운드 프롬프트.");
    }

    // ── startRound: common_hint ───────────────────────────────────────────────

    @Test
    void startRound_withCommonHint_includesItInPayload() {
        Scenario scenario = scenarioWith(List.of(
            round(null, null, 300),
            round("두 번째 라운드.", "공통 힌트 텍스트.", 300)
        ));
        Session session = roundSession();
        session.setCurrentRoundNumber(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 2);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().commonHint()).isEqualTo("공통 힌트 텍스트.");
    }

    @Test
    void startRound_commonHintNull_payloadCommonHintIsNull() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().commonHint()).isNull();
    }

    // ── startRound: payload fields ────────────────────────────────────────────

    @Test
    void startRound_payloadRoundNumber_matches() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        assertThat(captor.getValue().roundNumber()).isEqualTo(1);
    }

    @Test
    void startRound_deadlineAtIsStartedAtPlusTimeLimitSec() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 600)));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(roundSession()));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundStartedPayload> captor = ArgumentCaptor.forClass(RoundStartedPayload.class);
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), captor.capture());
        RoundStartedPayload payload = captor.getValue();
        assertThat(payload.deadlineAt() - payload.startedAt()).isEqualTo(600_000L);
    }

    // ── startRound: idempotency ───────────────────────────────────────────────

    @Test
    void startRound_idempotent_alreadyAtSameRound_reBroadcastsWithoutSaving() {
        Session session = roundSession();
        session.setCurrentRoundNumber(1);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        RoundEntity existing = new RoundEntity(SESSION_ID, 1, "기존 프롬프트", null,
            Instant.ofEpochMilli(1_000_000L), Instant.ofEpochMilli(1_600_000L));
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(Optional.of(existing));

        service.startRound(SESSION_ID, 1);

        verify(roundRepo, never()).save(any());
        verify(eventPublisher).publish(any(), eq("ROUND_STARTED"), any(RoundStartedPayload.class));
    }

    // ── startRound: OBJECTIVE_UPDATED per-player ────────────────────────────────

    @Test
    void startRound_emitsObjectiveUpdatedPerAssignedPlayer() {
        List<ScenarioCharacter> chars = List.of(
            new ScenarioCharacter("char-a", "A", null, null, null, null, null, null, null,
                List.of(new RoundObjective(1, "R1 목표 A"))),
            new ScenarioCharacter("char-b", "B", null, null, null, null, null, null, null,
                List.of(new RoundObjective(1, "R1 목표 B")))
        );
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(scenario.characters()).thenReturn(chars);
        when(scenario.roundCount()).thenReturn(1);

        Session session = roundSession();
        Player p1 = new Player("alice", true);
        p1.setAssignedCharacterId("char-a");
        Player p2 = new Player("bob", false);
        p2.setAssignedCharacterId("char-b");
        session.addPlayer(p1);
        session.addPlayer(p2);

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<ObjectiveUpdatedPayload> captor = ArgumentCaptor.forClass(ObjectiveUpdatedPayload.class);
        verify(eventPublisher, times(2)).publishToPlayer(
            eq("ABCDEF"), any(), eq(SESSION_ID.toString()), eq("OBJECTIVE_UPDATED"), captor.capture());

        List<ObjectiveUpdatedPayload> payloads = captor.getAllValues();
        assertThat(payloads).anySatisfy(p -> assertThat(p.objective()).isEqualTo("R1 목표 A"));
        assertThat(payloads).anySatisfy(p -> assertThat(p.objective()).isEqualTo("R1 목표 B"));
        assertThat(payloads).allSatisfy(p -> {
            assertThat(p.roundNumber()).isEqualTo(1);
            assertThat(p.totalRounds()).isEqualTo(1);
        });
    }

    @Test
    void startRound_playerWithoutAssignedCharacter_noObjectiveUpdated() {
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(scenario.characters()).thenReturn(List.of());

        Session session = roundSession();
        Player p = new Player("alice", true); // assignedCharacterId is null
        session.addPlayer(p);

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), eq("OBJECTIVE_UPDATED"), any());
    }

    @Test
    void startRound_characterWithoutObjectives_sendsNullObjective() {
        List<ScenarioCharacter> chars = List.of(
            new ScenarioCharacter("char-a", "A", null, null, null, null, null, null, null, null)
        );
        Scenario scenario = scenarioWith(List.of(round(null, null, 300)));
        when(scenario.characters()).thenReturn(chars);
        when(scenario.roundCount()).thenReturn(1);

        Session session = roundSession();
        Player p = new Player("alice", true);
        p.setAssignedCharacterId("char-a");
        session.addPlayer(p);

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<ObjectiveUpdatedPayload> captor = ArgumentCaptor.forClass(ObjectiveUpdatedPayload.class);
        verify(eventPublisher, times(1)).publishToPlayer(
            any(), any(), any(), eq("OBJECTIVE_UPDATED"), captor.capture());
        assertThat(captor.getValue().objective()).isNull();
    }

    // ── startRound: guard cases ───────────────────────────────────────────────

    @Test
    void startRound_sessionNotFound_silentlyDrops() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        service.startRound(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startRound_phaseEnded_silentlyDrops() {
        Session session = roundSession();
        session.setPhase("ended");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startRound(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startRound_stateNotRound_silentlyDrops() {
        Session session = roundSession();
        session.setState("tutorial");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startRound(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void startRound_unexpectedRoundNumber_silentlyDrops() {
        // currentRoundNumber is null → only round 1 is valid; requesting round 2 skips
        Session session = roundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.startRound(SESSION_ID, 2);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── startRound: DB persistence ────────────────────────────────────────────

    @Test
    void startRound_savesRoundEntityAndUpdatesSessionRoundNumber() {
        Scenario scenario = scenarioWith(List.of(round("프롬프트.", null, 300)));
        Session session = roundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.startRound(SESSION_ID, 1);

        ArgumentCaptor<RoundEntity> entityCaptor = ArgumentCaptor.forClass(RoundEntity.class);
        verify(roundRepo).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getRoundNumber()).isEqualTo(1);
        assertThat(entityCaptor.getValue().getPrompt()).isEqualTo("프롬프트.");
        assertThat(session.getCurrentRoundNumber()).isEqualTo(1);
    }
}
