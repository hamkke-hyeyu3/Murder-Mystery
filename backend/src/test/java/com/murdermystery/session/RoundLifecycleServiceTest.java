package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.PrivateTalkEndedPayload;
import com.murdermystery.ws.event.RoundEndedPayload;
import com.murdermystery.ws.event.RoundTurnsCompletePayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoundLifecycleServiceTest {

    private SessionRepository sessionRepo;
    private RoundRepository roundRepo;
    private ScenarioRepository scenarioRepo;
    private SessionEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private ScheduledExecutorService scheduler;
    private Clock clock;
    @SuppressWarnings("unchecked")
    private ObjectProvider<RoundService> roundServiceProvider;
    private RoundService mockRoundService;
    private PrivateTalkService privateTalkService;
    private RoundLifecycleService service;

    private final List<Runnable> capturedTasks = new ArrayList<>();
    private final List<ScheduledFuture<?>> capturedFutures = new ArrayList<>();

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        roundRepo = mock(RoundRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        eventPublisher = mock(SessionEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        scheduler = mock(ScheduledExecutorService.class);
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        roundServiceProvider = mock(ObjectProvider.class);
        mockRoundService = mock(RoundService.class);
        privateTalkService = mock(PrivateTalkService.class);
        capturedTasks.clear();
        capturedFutures.clear();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
            .thenAnswer(inv -> {
                capturedTasks.add(inv.getArgument(0, Runnable.class));
                ScheduledFuture<?> f = mock(ScheduledFuture.class);
                capturedFutures.add(f);
                return f;
            });

        when(roundServiceProvider.getObject()).thenReturn(mockRoundService);

        service = new RoundLifecycleService(
            sessionRepo, roundRepo, scenarioRepo, eventPublisher, txTemplate,
            scheduler, clock, roundServiceProvider, privateTalkService
        );
    }

    private Session roundSession(int roundNumber) {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setCurrentRoundNumber(roundNumber);
        return s;
    }

    private RoundEntity roundEntity(int roundNumber) {
        return new RoundEntity(SESSION_ID, roundNumber, "prompt", null,
            FIXED_NOW.minusSeconds(60), FIXED_NOW.plusSeconds(540));
    }

    private Scenario scenarioWithRounds(int totalRounds) {
        Scenario s = mock(Scenario.class);
        when(s.id()).thenReturn("toy-manor");
        when(s.roundCount()).thenReturn(totalRounds);
        return s;
    }

    private void stubHappyPath(int roundNumber, int totalRounds) {
        // Build mocks before passing to when() to avoid Mockito nested-stubbing interference
        Session session = roundSession(roundNumber);
        RoundEntity round = roundEntity(roundNumber);
        Scenario scenario = scenarioWithRounds(totalRounds);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, roundNumber))
            .thenReturn(Optional.of(round));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));
    }

    // ── onTurnsComplete ──

    @Test
    void onTurnsComplete_emitsRoundTurnsCompleteThenCallsEndRound() {
        stubHappyPath(1, 2);

        service.onTurnsComplete(SESSION_ID, 1);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ROUND_TURNS_COMPLETE"),
            any(RoundTurnsCompletePayload.class));
        inOrder.verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ROUND_ENDED"),
            any(RoundEndedPayload.class));
    }

    @Test
    void onTurnsComplete_cancelsPendingDeadlineFuture() {
        stubHappyPath(1, 2);

        service.scheduleRoundDeadline(SESSION_ID, 1, FIXED_NOW.plusSeconds(600));
        assertThat(capturedFutures).hasSize(1);
        ScheduledFuture<?> registeredFuture = capturedFutures.get(0);

        service.onTurnsComplete(SESSION_ID, 1);

        verify(registeredFuture).cancel(false);
    }

    // ── endRound: non-last round ──

    @Test
    void endRound_nonLastRound_callsStartRoundOnNext() {
        stubHappyPath(1, 3);

        service.endRound(SESSION_ID, 1, "turns_complete");

        verify(mockRoundService).startRound(SESSION_ID, 2);
        verify(eventPublisher, never()).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }

    // ── endRound: last round → vote ──

    @Test
    void endRound_lastRound_setsStateVoteAndEmitsSessionStateChanged() {
        Session session = roundSession(2);
        RoundEntity round = roundEntity(2);
        Scenario scenario = scenarioWithRounds(2);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, 2)).thenReturn(Optional.of(round));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.endRound(SESSION_ID, 2, "turns_complete");

        assertThat(session.getState()).isEqualTo("vote");

        ArgumentCaptor<SessionStateChangedPayload> captor =
            ArgumentCaptor.forClass(SessionStateChangedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("SESSION_STATE_CHANGED"), captor.capture());
        assertThat(captor.getValue().state()).isEqualTo("vote");
        assertThat(captor.getValue().turnOrder()).isNull();

        verify(roundServiceProvider, never()).getObject();
    }

    // ── endRound: idempotency ──

    @Test
    void endRound_isIdempotent_underConcurrentCalls() {
        stubHappyPath(1, 2);

        service.endRound(SESSION_ID, 1, "turns_complete");
        service.endRound(SESSION_ID, 1, "time_limit");

        verify(eventPublisher, times(1)).publish(eq(SESSION_ID.toString()), eq("ROUND_ENDED"), any());
    }

    // ── scheduleRoundDeadline ──

    @Test
    void scheduleRoundDeadline_capturedRunnableTriggersEndRoundWithTimeLimitReason() {
        stubHappyPath(1, 2);

        service.scheduleRoundDeadline(SESSION_ID, 1, FIXED_NOW.plusSeconds(600));
        assertThat(capturedTasks).hasSize(1);

        capturedTasks.get(0).run();

        ArgumentCaptor<RoundEndedPayload> captor = ArgumentCaptor.forClass(RoundEndedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ROUND_ENDED"), captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("time_limit");
        assertThat(captor.getValue().roundNumber()).isEqualTo(1);
    }

    // ── endRound: guard cases ──

    @Test
    void endRound_skipsWhenSessionStateMismatched() {
        Session session = roundSession(1);
        session.setState("tutorial");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.endRound(SESSION_ID, 1, "turns_complete");

        verify(eventPublisher, never()).publish(any(), eq("ROUND_ENDED"), any());
        verify(roundServiceProvider, never()).getObject();
    }

    @Test
    void endRound_persistsEndedAtOnRoundEntity() {
        Session session = roundSession(1);
        RoundEntity round = roundEntity(1);
        Scenario scenario = scenarioWithRounds(2);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(roundRepo.findBySessionIdAndRoundNumber(SESSION_ID, 1)).thenReturn(Optional.of(round));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(scenario));

        service.endRound(SESSION_ID, 1, "turns_complete");

        assertThat(round.getEndedAt()).isEqualTo(FIXED_NOW);
        verify(roundRepo).save(round);
    }

    // ── endRound: private talk boundary integration ──

    @Test
    void endRound_callsEndByRoundBoundaryBeforeRoundEnded() {
        stubHappyPath(1, 2);

        service.endRound(SESSION_ID, 1, "turns_complete");

        InOrder inOrder = inOrder(privateTalkService, eventPublisher);
        inOrder.verify(privateTalkService).endByRoundBoundary(SESSION_ID, 1);
        inOrder.verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("ROUND_ENDED"), any());
    }

    @Test
    void endRound_skippedSession_doesNotCallEndByRoundBoundary() {
        Session session = roundSession(1);
        session.setState("tutorial");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.endRound(SESSION_ID, 1, "turns_complete");

        verify(privateTalkService, never()).endByRoundBoundary(any(), anyInt());
    }
}
