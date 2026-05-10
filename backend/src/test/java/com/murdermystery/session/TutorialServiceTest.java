package com.murdermystery.session;

import com.murdermystery.ws.event.SessionStateChangedPayload;
import com.murdermystery.ws.event.TutorialAckedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TutorialServiceTest {

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private TutorialService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID DEVICE_A = UUID.randomUUID();
    private static final UUID DEVICE_B = UUID.randomUUID();
    private static final UUID DEVICE_C = UUID.randomUUID();
    private static final UUID UNKNOWN_DEVICE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        service = new TutorialService(sessionRepo, txTemplate, eventPublisher);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Session tutorialSessionWith3Players() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("tutorial");
        s.addPlayer(new Player("alice", true, DEVICE_A));
        s.addPlayer(new Player("bob", false, DEVICE_B));
        s.addPlayer(new Player("charlie", false, DEVICE_C));
        return s;
    }

    // ── acknowledge: guard cases ──────────────────────────────────────────────

    @Test
    void acknowledge_unknownSession_throwsSessionNotFound() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, DEVICE_A))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void acknowledge_phaseLobby_throwsTutorialPhaseRequired() {
        Session s = tutorialSessionWith3Players();
        s.setPhase("lobby");
        s.setState(null);
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, DEVICE_A))
            .isInstanceOf(TutorialPhaseRequiredException.class);
    }

    @Test
    void acknowledge_stateIntro_throwsTutorialPhaseRequired() {
        Session s = tutorialSessionWith3Players();
        s.setState("intro");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, DEVICE_A))
            .isInstanceOf(TutorialPhaseRequiredException.class);
    }

    @Test
    void acknowledge_stateCharacterAssignment_throwsTutorialPhaseRequired() {
        Session s = tutorialSessionWith3Players();
        s.setState("character_assignment");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, DEVICE_A))
            .isInstanceOf(TutorialPhaseRequiredException.class);
    }

    @Test
    void acknowledge_stateRound_throwsTutorialPhaseRequired() {
        Session s = tutorialSessionWith3Players();
        s.setState("round");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, DEVICE_A))
            .isInstanceOf(TutorialPhaseRequiredException.class);
    }

    @Test
    void acknowledge_unknownDevice_throwsPlayerNotInSession() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(tutorialSessionWith3Players()));

        assertThatThrownBy(() -> service.acknowledge(SESSION_ID, UNKNOWN_DEVICE))
            .isInstanceOf(PlayerNotInSessionException.class);
    }

    // ── acknowledge: partial progress ─────────────────────────────────────────

    @Test
    void acknowledge_singlePlayerOf3_returnsProgressWithNoStateChange() {
        Session s = tutorialSessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        TutorialAckResponse response = service.acknowledge(SESSION_ID, DEVICE_A);

        assertThat(response.acked()).isEqualTo(1);
        assertThat(response.total()).isEqualTo(3);
        assertThat(response.state()).isEqualTo("tutorial");
        assertThat(s.getState()).isEqualTo("tutorial");
    }

    @Test
    void acknowledge_partialProgress_broadcastsTutorialAcked() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(tutorialSessionWith3Players()));

        service.acknowledge(SESSION_ID, DEVICE_A);

        ArgumentCaptor<TutorialAckedPayload> captor = ArgumentCaptor.forClass(TutorialAckedPayload.class);
        verify(eventPublisher).publish(any(), eq("TUTORIAL_ACKED"), captor.capture());
        assertThat(captor.getValue().acked()).isEqualTo(1);
        assertThat(captor.getValue().total()).isEqualTo(3);
    }

    @Test
    void acknowledge_partialProgress_doesNotBroadcastSessionStateChanged() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(tutorialSessionWith3Players()));

        service.acknowledge(SESSION_ID, DEVICE_A);

        verify(eventPublisher, never()).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }

    // ── acknowledge: last player → round transition ───────────────────────────

    @Test
    void acknowledge_lastPlayer_transitionsToRound() {
        Session s = tutorialSessionWith3Players();
        // pre-ack A and B
        s.getPlayers().get(0).acknowledgeTutorial(java.time.Instant.now());
        s.getPlayers().get(1).acknowledgeTutorial(java.time.Instant.now());
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        TutorialAckResponse response = service.acknowledge(SESSION_ID, DEVICE_C);

        assertThat(s.getState()).isEqualTo("round");
        assertThat(response.acked()).isEqualTo(3);
        assertThat(response.total()).isEqualTo(3);
        assertThat(response.state()).isEqualTo("round");
    }

    @Test
    void acknowledge_lastPlayer_broadcastsAckedThenStateChangedInOrder() {
        Session s = tutorialSessionWith3Players();
        s.getPlayers().get(0).acknowledgeTutorial(java.time.Instant.now());
        s.getPlayers().get(1).acknowledgeTutorial(java.time.Instant.now());
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.acknowledge(SESSION_ID, DEVICE_C);

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publish(any(), eq("TUTORIAL_ACKED"), any());
        order.verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"),
            argThat(p -> p instanceof SessionStateChangedPayload sc && "round".equals(sc.state())));
    }

    // ── acknowledge: idempotency ───────────────────────────────────────────────

    @Test
    void acknowledge_idempotent_doubleAckByNonLastPlayer_doesNotReBroadcast() {
        Session s = tutorialSessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.acknowledge(SESSION_ID, DEVICE_A); // first ack
        reset(eventPublisher);
        service.acknowledge(SESSION_ID, DEVICE_A); // second ack — same player, state still tutorial

        verify(eventPublisher, never()).publish(any(), eq("TUTORIAL_ACKED"), any());
        verify(eventPublisher, never()).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }

    // ── enterTutorialState ────────────────────────────────────────────────────

    @Test
    void enterTutorialState_normalFlow_setsTutorialAndBroadcasts() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("character_assignment");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.enterTutorialState(SESSION_ID);

        assertThat(s.getState()).isEqualTo("tutorial");
        ArgumentCaptor<SessionStateChangedPayload> captor =
            ArgumentCaptor.forClass(SessionStateChangedPayload.class);
        verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), captor.capture());
        assertThat(captor.getValue().state()).isEqualTo("tutorial");
        assertThat(captor.getValue().turnOrder()).isNull();
    }

    @Test
    void enterTutorialState_stateAlreadyTutorial_republishes() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("tutorial");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.enterTutorialState(SESSION_ID);

        assertThat(s.getState()).isEqualTo("tutorial");
        ArgumentCaptor<SessionStateChangedPayload> captor =
            ArgumentCaptor.forClass(SessionStateChangedPayload.class);
        verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"), captor.capture());
        assertThat(captor.getValue().state()).isEqualTo("tutorial");
    }

    @Test
    void enterTutorialState_stateAlreadyRound_silentlyDrops() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("round");
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.enterTutorialState(SESSION_ID);

        assertThat(s.getState()).isEqualTo("round");
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void enterTutorialState_phaseEnded_silentlyDrops() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("ended");
        s.setState(null);
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.of(s));

        service.enterTutorialState(SESSION_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void enterTutorialState_sessionNotFound_silentlyDrops() {
        when(sessionRepo.findById(SESSION_ID)).thenReturn(Optional.empty());

        service.enterTutorialState(SESSION_ID);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
