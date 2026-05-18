package com.murdermystery.session;

import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.PrivateTalkEndedPayload;
import com.murdermystery.ws.event.PrivateTalkRequestedPayload;
import com.murdermystery.ws.event.PrivateTalkStartedPayload;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PrivateTalkServiceTest {

    private SessionRepository sessionRepo;
    private PrivateTalkRepository talkRepo;
    private ScenarioRepository scenarioRepo;
    private SessionEventPublisher eventPublisher;
    private TransactionTemplate txTemplate;
    private ScheduledExecutorService scheduler;
    private PrivateTalkService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID CHARLIE_ID = UUID.randomUUID();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String INVITE_CODE = "XYZABC";

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        talkRepo = mock(PrivateTalkRepository.class);
        scenarioRepo = mock(ScenarioRepository.class);
        eventPublisher = mock(SessionEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        scheduler = Executors.newSingleThreadScheduledExecutor();

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        when(talkRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new PrivateTalkService(sessionRepo, talkRepo, scenarioRepo,
                eventPublisher, txTemplate, scheduler,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Session inProgressRoundSession() {
        Session s = new Session(INVITE_CODE, "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        s.setCurrentRoundNumber(1);
        Player alice = new Player("alice", true, UUID.randomUUID());
        Player bob = new Player("bob", false, UUID.randomUUID());
        setPlayerId(alice, ALICE_ID);
        setPlayerId(bob, BOB_ID);
        s.addPlayer(alice);
        s.addPlayer(bob);
        return s;
    }

    private Scenario allowedScenario() {
        return mock(Scenario.class, inv -> {
            if (inv.getMethod().getName().equals("allowPrivateTalk")) return true;
            if (inv.getMethod().getName().equals("roundCount")) return 3;
            return null;
        });
    }

    private Scenario disallowedScenario() {
        return mock(Scenario.class, inv -> {
            if (inv.getMethod().getName().equals("allowPrivateTalk")) return false;
            return null;
        });
    }

    private PrivateTalk activeTalk(UUID requesterId, UUID targetId) {
        return new PrivateTalk(SESSION_ID, 1, requesterId, targetId, FIXED_NOW);
    }

    private void setPlayerId(Player p, UUID id) {
        try {
            var f = Player.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── request() tests ──────────────────────────────────────────────────

    @Test
    void request_happyPath_savesRowAndPublishesToBothPlayers() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(allowedScenario()));
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        service.request(SESSION_ID, ALICE_ID, BOB_ID);

        verify(talkRepo).save(any(PrivateTalk.class));

        // both requester and target receive PRIVATE_TALK_REQUESTED
        ArgumentCaptor<String> playerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(2)).publishToPlayer(
                eq(INVITE_CODE), playerIdCaptor.capture(),
                eq(SESSION_ID.toString()), eq("PRIVATE_TALK_REQUESTED"),
                any(PrivateTalkRequestedPayload.class));
        List<String> notifiedIds = playerIdCaptor.getAllValues();
        assertThat(notifiedIds).containsExactlyInAnyOrder(ALICE_ID.toString(), BOB_ID.toString());
    }

    @Test
    void request_scenarioDisallowed_doesNothing() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(disallowedScenario()));
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        service.request(SESSION_ID, ALICE_ID, BOB_ID);

        verify(talkRepo, never()).save(any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), any(), any());
    }

    @Test
    void request_sessionNotInRound_doesNothing() {
        Session session = new Session(INVITE_CODE, "toy-manor");
        session.setPhase("in_progress");
        session.setState("vote");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        service.request(SESSION_ID, ALICE_ID, BOB_ID);

        verify(talkRepo, never()).save(any());
    }

    @Test
    void request_selfRequest_doesNothing() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(allowedScenario()));
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        service.request(SESSION_ID, ALICE_ID, ALICE_ID);

        verify(talkRepo, never()).save(any());
    }

    @Test
    void request_nonParticipantTarget_doesNothing() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(allowedScenario()));
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        service.request(SESSION_ID, ALICE_ID, CHARLIE_ID);  // charlie not in session

        verify(talkRepo, never()).save(any());
    }

    @Test
    void request_busySession_doesNothing() {
        Session session = inProgressRoundSession();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(scenarioRepo.findById("toy-manor")).thenReturn(Optional.of(allowedScenario()));
        when(talkRepo.findActiveBySessionId(SESSION_ID))
                .thenReturn(Optional.of(activeTalk(ALICE_ID, BOB_ID)));

        service.request(SESSION_ID, ALICE_ID, BOB_ID);

        verify(talkRepo, never()).save(any(PrivateTalk.class));
    }

    // ── accept() tests ───────────────────────────────────────────────────

    @Test
    void accept_happyPath_broadcastsStarted() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.accept(SESSION_ID, talkId, BOB_ID);

        assertThat(talk.isStarted()).isTrue();
        verify(eventPublisher).publish(eq(SESSION_ID.toString()),
                eq("PRIVATE_TALK_STARTED"), any(PrivateTalkStartedPayload.class));
    }

    @Test
    void accept_wrongAccepter_doesNotBroadcast() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.accept(SESSION_ID, talkId, ALICE_ID);  // alice is requester, not target

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void accept_alreadyTerminated_doesNotBroadcast() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        talk.markEnded("REJECTED", FIXED_NOW);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.accept(SESSION_ID, talkId, BOB_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── reject() tests ───────────────────────────────────────────────────

    @Test
    void reject_happyPath_savesEndReasonWithNoEvent() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.reject(SESSION_ID, talkId, BOB_ID);

        assertThat(talk.getEndReason()).isEqualTo("REJECTED");
        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), any(), any());
    }

    @Test
    void reject_wrongRejector_doesNothing() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.reject(SESSION_ID, talkId, CHARLIE_ID);

        assertThat(talk.getEndReason()).isNull();
    }

    // ── end() tests ───────────────────────────────────────────────────────

    @Test
    void end_happyPath_broadcastsEnded() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        talk.markStarted(FIXED_NOW);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.end(SESSION_ID, talkId, ALICE_ID);

        assertThat(talk.getEndReason()).isEqualTo("USER_ENDED");
        verify(eventPublisher).publish(eq(SESSION_ID.toString()),
                eq("PRIVATE_TALK_ENDED"), any(PrivateTalkEndedPayload.class));
    }

    @Test
    void end_notStarted_doesNotBroadcast() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        // not started (started_at == null)
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.end(SESSION_ID, talkId, ALICE_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void end_nonParticipantCaller_doesNotBroadcast() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        talk.markStarted(FIXED_NOW);
        UUID talkId = talk.getId();

        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.end(SESSION_ID, talkId, CHARLIE_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    // ── timeout() tests ──────────────────────────────────────────────────

    @Test
    void timeoutRequest_setsTimeoutEndReason_withNoEvent() {
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        UUID talkId = talk.getId();
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.timeoutRequest(talkId);

        assertThat(talk.getEndReason()).isEqualTo("TIMEOUT");
        verify(eventPublisher, never()).publish(any(), any(), any());
        verify(eventPublisher, never()).publishToPlayer(any(), any(), any(), any(), any());
    }

    @Test
    void timeoutRequest_alreadyTerminated_doesNotOverwrite() {
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        talk.markEnded("REJECTED", FIXED_NOW);
        UUID talkId = talk.getId();
        when(talkRepo.findById(talkId)).thenReturn(Optional.of(talk));

        service.timeoutRequest(talkId);

        assertThat(talk.getEndReason()).isEqualTo("REJECTED");  // unchanged
    }

    // ── endByRoundBoundary() tests ────────────────────────────────────────

    @Test
    void endByRoundBoundary_activeTalk_broadcastsEnded() {
        Session session = inProgressRoundSession();
        PrivateTalk talk = activeTalk(ALICE_ID, BOB_ID);
        talk.markStarted(FIXED_NOW);
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.of(talk));
        when(talkRepo.findById(talk.getId())).thenReturn(Optional.of(talk));
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(session));

        service.endByRoundBoundary(SESSION_ID, 1);

        assertThat(talk.getEndReason()).isEqualTo("ROUND_BOUNDARY");
        ArgumentCaptor<PrivateTalkEndedPayload> captor =
                ArgumentCaptor.forClass(PrivateTalkEndedPayload.class);
        verify(eventPublisher).publish(eq(SESSION_ID.toString()),
                eq("PRIVATE_TALK_ENDED"), captor.capture());
        assertThat(captor.getValue().endReason()).isEqualTo("ROUND_BOUNDARY");
    }

    @Test
    void endByRoundBoundary_noActiveTalk_doesNothing() {
        when(talkRepo.findActiveBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        service.endByRoundBoundary(SESSION_ID, 1);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
