package com.murdermystery.session;

import com.murdermystery.ws.SurveySubmitRequest;
import com.murdermystery.ws.event.SessionEndedPayload;
import com.murdermystery.ws.event.SurveyResponseRecordedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SurveyServiceTest {

    private SessionRepository sessionRepo;
    private SurveyResponseRepository surveyResponseRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private EndingService endingService;
    private SurveyService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_PLAYER = UUID.randomUUID();

    private UUID playerAId;
    private UUID playerBId;
    private UUID playerCId;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        surveyResponseRepo = mock(SurveyResponseRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);
        endingService = mock(EndingService.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        service = new SurveyService(sessionRepo, surveyResponseRepo, txTemplate, eventPublisher, endingService);
    }

    private Session surveySessionWith3Players() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("survey");
        Player a = new Player("alice", true);
        Player b = new Player("bob", false);
        Player c = new Player("charlie", false);
        playerAId = a.getId();
        playerBId = b.getId();
        playerCId = c.getId();
        s.addPlayer(a);
        s.addPlayer(b);
        s.addPlayer(c);
        return s;
    }

    // ── guard cases ───────────────────────────────────────────────────────────

    @Test
    void submit_unknownSession_throwsSessionNotFound() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(SESSION_ID, UNKNOWN_PLAYER, emptyRequest()))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void submit_stateNotSurvey_throwsSurveyPhaseRequired() {
        Session s = surveySessionWith3Players();
        s.setState("round");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submit(SESSION_ID, playerAId, emptyRequest()))
            .isInstanceOf(SurveyPhaseRequiredException.class);
    }

    @Test
    void submit_unknownPlayer_throwsPlayerNotInSession() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(surveySessionWith3Players()));

        assertThatThrownBy(() -> service.submit(SESSION_ID, UNKNOWN_PLAYER, emptyRequest()))
            .isInstanceOf(PlayerNotInSessionException.class);
    }

    @Test
    void submit_scoreOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.submit(SESSION_ID, playerAId,
            new SurveySubmitRequest(6, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit_freeTextOver80_throwsIllegalArgument() {
        String longText = "A".repeat(81);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(surveySessionWith3Players()));
        when(surveyResponseRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(SESSION_ID, playerAId,
            new SurveySubmitRequest(null, null, longText)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── broadcast on first submit ─────────────────────────────────────────────

    @Test
    void submit_firstSubmit_broadcastsSurveyResponseRecorded() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(surveySessionWith3Players()));
        when(surveyResponseRepo.findById(any())).thenReturn(Optional.empty());
        when(surveyResponseRepo.countBySessionId(SESSION_ID)).thenReturn(1L);

        service.submit(SESSION_ID, playerAId, new SurveySubmitRequest(4, 3, "good"));

        ArgumentCaptor<SurveyResponseRecordedPayload> captor =
            ArgumentCaptor.forClass(SurveyResponseRecordedPayload.class);
        verify(eventPublisher).publish(any(), eq("SURVEY_RESPONSE_RECORDED"), captor.capture());
        assertThat(captor.getValue().respondedCount()).isEqualTo(1);
        assertThat(captor.getValue().totalCount()).isEqualTo(3);
    }

    @Test
    void submit_dismissCountsForProgress() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(surveySessionWith3Players()));
        when(surveyResponseRepo.findById(any())).thenReturn(Optional.empty());
        when(surveyResponseRepo.countBySessionId(SESSION_ID)).thenReturn(1L);

        service.submit(SESSION_ID, playerAId, emptyRequest());

        verify(eventPublisher).publish(any(), eq("SURVEY_RESPONSE_RECORDED"), any());
    }

    // ── all done → endSession ──────────────────────────────────────────────────

    @Test
    void submit_lastPlayer_callsEndSession() {
        Session s = surveySessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));
        when(surveyResponseRepo.findById(any())).thenReturn(Optional.empty());
        when(surveyResponseRepo.countBySessionId(SESSION_ID)).thenReturn(3L); // all 3

        // endSession needs its own DB lookup — return state='survey' then set to 'ended'
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.submit(SESSION_ID, playerCId, emptyRequest());

        verify(eventPublisher).publish(any(), eq("SESSION_ENDED"), any());
        verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), argThat(
            p -> p instanceof com.murdermystery.ws.event.SessionStateChangedPayload sc
                && "ended".equals(sc.state())
        ));
    }

    // ── idempotency ────────────────────────────────────────────────────────────

    @Test
    void submit_idempotent_secondCallDoesNotReBroadcast() {
        Session s = surveySessionWith3Players();
        SurveyResponse existing = new SurveyResponse(SESSION_ID, playerAId, 3, 4, "ok", java.time.Instant.now());
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));
        when(surveyResponseRepo.findById(any())).thenReturn(Optional.of(existing));
        when(surveyResponseRepo.countBySessionId(SESSION_ID)).thenReturn(1L);

        service.submit(SESSION_ID, playerAId, new SurveySubmitRequest(5, 5, "great"));

        verify(eventPublisher, never()).publish(any(), eq("SURVEY_RESPONSE_RECORDED"), any());
    }

    // ── endSession idempotent ─────────────────────────────────────────────────

    @Test
    void endSession_idempotent_secondCallDoesNotBroadcastAgain() {
        Session s = surveySessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.endSession(SESSION_ID.toString());
        reset(eventPublisher, sessionRepo);

        service.endSession(SESSION_ID.toString()); // second call

        verify(eventPublisher, never()).publish(any(), eq("SESSION_ENDED"), any());
    }

    @Test
    void endSession_alreadyEndedInDb_doesNotBroadcast() {
        Session s = surveySessionWith3Players();
        s.setState("ended");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.clearEndedSessionsForTest();
        service.endSession(SESSION_ID.toString());

        verify(eventPublisher, never()).publish(any(), eq("SESSION_ENDED"), any());
    }

    private SurveySubmitRequest emptyRequest() {
        return new SurveySubmitRequest(null, null, null);
    }
}
