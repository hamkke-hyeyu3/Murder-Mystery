package com.murdermystery.session;

import com.murdermystery.ws.event.ForceProgressAvailablePayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ForceProgressTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID SESSION_ID = UUID.randomUUID();

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private MissionEndingHelper endingHelper;
    private ScheduledExecutorService scheduler;
    private Clock clock;
    private ForceProgressService service;

    // captured player IDs, set by helpers
    private UUID hostId;
    private UUID guestId;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        endingHelper = mock(MissionEndingHelper.class);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        clock = Clock.fixed(T0, ZoneOffset.UTC);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        doAnswer(inv -> {
            var consumer = inv.getArgument(0, java.util.function.Consumer.class);
            consumer.accept(null);
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        service = new ForceProgressService(
            sessionRepo, txTemplate, eventPublisher, endingHelper, scheduler, clock);
        service.forceProgressDelayMs = 50; // fast for tests
    }

    @AfterEach
    void tearDown() {
        service.cancelPendingForTest();
        scheduler.shutdownNow();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Session missionSessionHostNotChecked() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("mission");
        Player host = new Player("alice", true);
        Player guest = new Player("bob", false);
        hostId = host.getId();
        guestId = guest.getId();
        s.addPlayer(host);
        s.addPlayer(guest);
        return s;
    }

    private Session missionSessionHostChecked() {
        Session s = missionSessionHostNotChecked();
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .acknowledgeMission(T0.minusSeconds(60));
        return s;
    }

    // ── scheduleEvaluation / evaluate ─────────────────────────────────────────

    @Test
    void _exposed3MinAfterFirstComplete_hostCheckedOthersIncomplete_broadcastsHostScope()
        throws InterruptedException {
        Session s = missionSessionHostChecked(); // host checked, guest not
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        Thread.sleep(200); // wait for scheduled task

        ArgumentCaptor<ForceProgressAvailablePayload> cap =
            ArgumentCaptor.forClass(ForceProgressAvailablePayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("FORCE_PROGRESS_AVAILABLE"), cap.capture());
        assertThat(cap.getValue().scope()).isEqualTo("host");
    }

    @Test
    void _scheduleEvaluation_idempotent_secondCallIsIgnored() throws InterruptedException {
        Session s = missionSessionHostChecked();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        service.scheduleEvaluation(SESSION_ID); // should be no-op
        Thread.sleep(200);

        // evaluator fires only once
        verify(eventPublisher, times(1)).publish(any(), eq("FORCE_PROGRESS_AVAILABLE"), any());
    }

    @Test
    void _NB3_broadcastsWhenHostOfflineAndIncomplete() throws InterruptedException {
        Session s = missionSessionHostNotChecked();
        // host last seen 45s ago (> 30s threshold)
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(45));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        Thread.sleep(200);

        ArgumentCaptor<ForceProgressAvailablePayload> cap =
            ArgumentCaptor.forClass(ForceProgressAvailablePayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("FORCE_PROGRESS_AVAILABLE"), cap.capture());
        assertThat(cap.getValue().scope()).isEqualTo("all");
    }

    @Test
    void _NB3_hostOnline_incompleteHost_broadcastsHostScope() throws InterruptedException {
        Session s = missionSessionHostNotChecked();
        // host last seen 5s ago (online — within 30s threshold)
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(5));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        Thread.sleep(200);

        ArgumentCaptor<ForceProgressAvailablePayload> cap =
            ArgumentCaptor.forClass(ForceProgressAvailablePayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()), eq("FORCE_PROGRESS_AVAILABLE"), cap.capture());
        assertThat(cap.getValue().scope()).isEqualTo("host");
    }

    @Test
    void _cancel_preventsEvaluatorFromFiring() throws InterruptedException {
        Session s = missionSessionHostChecked();
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        service.cancel(SESSION_ID); // natural completion path
        Thread.sleep(200);

        verify(eventPublisher, never()).publish(any(), eq("FORCE_PROGRESS_AVAILABLE"), any());
    }

    @Test
    void _evaluate_allChecked_emitsNothing() throws InterruptedException {
        Session s = missionSessionHostNotChecked();
        s.getPlayers().forEach(p -> p.acknowledgeMission(T0));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.scheduleEvaluation(SESSION_ID);
        Thread.sleep(200);

        verify(eventPublisher, never()).publish(any(), eq("FORCE_PROGRESS_AVAILABLE"), any());
    }

    // ── forceProgress ─────────────────────────────────────────────────────────

    @Test
    void _forceProgressTransitionsToEnding() {
        Session s = missionSessionHostNotChecked();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.forceProgress(SESSION_ID, hostId);

        verify(endingHelper).transitionToEnding(s);
        verify(endingHelper).broadcastEndingTransition(SESSION_ID.toString());
        // incomplete players retain null mission_checked_at
        assertThat(s.getPlayers().stream()
            .filter(p -> p.getMissionCheckedAt() != null).count()).isEqualTo(0L);
    }

    @Test
    void _forceProgress_nonHost_NB3Active_allowed() {
        Session s = missionSessionHostNotChecked();
        // host offline (45s ago) and not checked — NB3 active
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(45));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.forceProgress(SESSION_ID, guestId); // non-host

        verify(endingHelper).transitionToEnding(s);
    }

    @Test
    void _forceProgress_nonHost_NB3Inactive_hostOnline_throws() {
        Session s = missionSessionHostNotChecked();
        // host online (5s ago) — NB3 inactive
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(5));
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.forceProgress(SESSION_ID, guestId))
            .isInstanceOf(ForceProgressNotAvailableException.class);
        verify(endingHelper, never()).transitionToEnding(any());
    }

    @Test
    void _forceProgress_nonHost_hostChecked_NB3Inactive_throws() {
        Session s = missionSessionHostChecked(); // host checked — NB3 inactive
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(60)); // offline but checked
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.forceProgress(SESSION_ID, guestId))
            .isInstanceOf(ForceProgressNotAvailableException.class);
        verify(endingHelper, never()).transitionToEnding(any());
    }

    @Test
    void _forceProgress_wrongState_throws() {
        Session s = missionSessionHostNotChecked();
        s.setState("ending"); // already ended
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.forceProgress(SESSION_ID, hostId))
            .isInstanceOf(MissionPhaseRequiredException.class);
        verify(endingHelper, never()).transitionToEnding(any());
    }

    // ── NB3 does not apply to other host actions ──────────────────────────────

    @Test
    void _NB3_doesNotApplyToOtherHostActions() {
        // ForceProgressService is the sole owner of NB3 logic.
        // Other host services (e.g. StartGameService) must not be affected.
        // This test verifies ForceProgressService isolation: a non-host calling
        // forceProgress while NB3 is INACTIVE correctly throws without leaking
        // NB3 authority to any other code path.

        // We reuse the NB3-inactive case above; the real guard is that no
        // other service (StartGameService etc.) imports or references
        // ForceProgressService for authorization decisions.

        // Ensure ForceProgressService.forceProgress with NB3-inactive non-host → exception only
        Session s = missionSessionHostNotChecked();
        s.getPlayers().stream().filter(Player::isHost).findFirst().get()
            .setLastSeenAt(T0.minusSeconds(5)); // online
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        List<Class<?>> callerClasses = List.of(
            com.murdermystery.session.StartGameService.class
        );
        for (Class<?> cls : callerClasses) {
            assertThat(List.of(cls.getDeclaredFields()))
                .as(cls.getSimpleName() + " must not hold ForceProgressService field")
                .noneMatch(f -> f.getType().equals(ForceProgressService.class));
        }

        assertThatThrownBy(() -> service.forceProgress(SESSION_ID, guestId))
            .isInstanceOf(ForceProgressNotAvailableException.class);
    }
}
