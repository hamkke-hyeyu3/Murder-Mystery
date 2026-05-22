package com.murdermystery.session;

import com.murdermystery.ws.event.DebriefStartedPayload;
import com.murdermystery.ws.event.EndingStartedPayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import com.murdermystery.ws.event.SurveyAvailablePayload;
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

class EndingServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private ScheduledExecutorService scheduler;
    private EndingService service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        when(sessionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new EndingService(sessionRepo, txTemplate, eventPublisher, scheduler);
        service.debriefDelayMs = 50;
        service.surveyDelayMs = 2000; // large enough to avoid survey firing during debrief assertions
    }

    @AfterEach
    void tearDown() {
        service.cancelPendingForTest();
        scheduler.shutdownNow();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Session endingSession() {
        Session s = new Session("ENDAB1", "toy-manor");
        s.setPhase("in_progress");
        s.setState("ending");
        return s;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void startEnding_broadcasts_ENDING_STARTED_immediately() {
        service.startEnding(SESSION_ID.toString());

        var typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        assertThat(typeCaptor.getAllValues()).contains("SESSION_STATE_CHANGED", "ENDING_STARTED");
    }

    @Test
    void startEnding_SESSION_STATE_CHANGED_payload_has_ending_state() {
        service.startEnding(SESSION_ID.toString());

        var payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), eq("SESSION_STATE_CHANGED"), payloadCaptor.capture());
        SessionStateChangedPayload payload = (SessionStateChangedPayload) payloadCaptor.getValue();
        assertThat(payload.state()).isEqualTo("ending");
    }

    @Test
    void startEnding_idempotent_on_double_call() {
        service.startEnding(SESSION_ID.toString());
        service.startEnding(SESSION_ID.toString());

        // Only one SESSION_STATE_CHANGED(ending) should be published
        verify(eventPublisher, times(1)).publish(eq(SESSION_ID.toString()), eq("ENDING_STARTED"), any());
    }

    @Test
    void startEnding_transitionsToDebrief_afterDelay() throws InterruptedException {
        Session s = endingSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.startEnding(SESSION_ID.toString());
        Thread.sleep(200); // wait for debrief delay (50ms) + buffer

        var typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        assertThat(typeCaptor.getAllValues()).contains("DEBRIEF_STARTED");
        assertThat(s.getState()).isEqualTo("debrief");
    }

    @Test
    void startEnding_SESSION_STATE_CHANGED_debrief_sent_before_DEBRIEF_STARTED() throws InterruptedException {
        Session s = endingSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.startEnding(SESSION_ID.toString());
        Thread.sleep(200);

        var typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        List<String> types = typeCaptor.getAllValues();
        int debriefChanged = types.lastIndexOf("SESSION_STATE_CHANGED");
        int debriefStarted = types.lastIndexOf("DEBRIEF_STARTED");
        assertThat(debriefChanged).isLessThan(debriefStarted);
    }

    @Test
    void startEnding_transitionsToSurvey_afterDelay() throws InterruptedException {
        Session s = endingSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        // Use fast delays for full chain: ending→debrief(50ms)→survey(60ms)
        service.debriefDelayMs = 50;
        service.surveyDelayMs = 60;
        service.startEnding(SESSION_ID.toString());
        Thread.sleep(400); // wait for full chain (110ms) + buffer

        var typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, atLeastOnce()).publish(eq(SESSION_ID.toString()), typeCaptor.capture(), any());
        assertThat(typeCaptor.getAllValues()).contains("SURVEY_AVAILABLE");
        assertThat(s.getState()).isEqualTo("survey");
    }

    @Test
    void startEnding_skips_debrief_if_session_not_in_ending_state() throws InterruptedException {
        Session s = endingSession();
        s.setState("survey"); // already advanced
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.startEnding(SESSION_ID.toString());
        Thread.sleep(200);

        verify(eventPublisher, never()).publish(eq(SESSION_ID.toString()), eq("DEBRIEF_STARTED"), any());
    }
}
