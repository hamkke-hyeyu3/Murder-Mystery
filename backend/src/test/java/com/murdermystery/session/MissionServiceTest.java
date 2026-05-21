package com.murdermystery.session;

import com.murdermystery.ws.event.MissionCheckCompletePayload;
import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MissionServiceTest {

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private MissionService service;

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_PLAYER = UUID.randomUUID();

    // captured in missionSessionWith3Players()
    private UUID playerAId;
    private UUID playerBId;
    private UUID playerCId;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        txTemplate = mock(TransactionTemplate.class);
        eventPublisher = mock(SessionEventPublisher.class);

        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });

        service = new MissionService(sessionRepo, txTemplate, eventPublisher);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Session missionSessionWith3Players() {
        Session s = new Session("ABCDEF", "toy-manor");
        s.setPhase("in_progress");
        s.setState("mission");
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
    void checkComplete_unknownSession_throwsSessionNotFound() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkComplete(SESSION_ID, UNKNOWN_PLAYER))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void checkComplete_stateRound_throwsMissionPhaseRequired() {
        Session s = missionSessionWith3Players();
        s.setState("round");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.checkComplete(SESSION_ID, playerAId))
            .isInstanceOf(MissionPhaseRequiredException.class);
    }

    @Test
    void checkComplete_stateVote_throwsMissionPhaseRequired() {
        Session s = missionSessionWith3Players();
        s.setState("vote");
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.checkComplete(SESSION_ID, playerAId))
            .isInstanceOf(MissionPhaseRequiredException.class);
    }

    @Test
    void checkComplete_unknownPlayer_throwsPlayerNotInSession() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(missionSessionWith3Players()));

        assertThatThrownBy(() -> service.checkComplete(SESSION_ID, UNKNOWN_PLAYER))
            .isInstanceOf(PlayerNotInSessionException.class);
    }

    // ── partial progress ──────────────────────────────────────────────────────

    @Test
    void checkComplete_singlePlayerOf3_stateStaysMission() {
        Session s = missionSessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.checkComplete(SESSION_ID, playerAId);

        assertThat(s.getState()).isEqualTo("mission");
        verify(eventPublisher, never()).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }

    @Test
    void checkComplete_partialProgress_broadcastsMissionCheckComplete() {
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(missionSessionWith3Players()));

        service.checkComplete(SESSION_ID, playerAId);

        ArgumentCaptor<MissionCheckCompletePayload> captor =
            ArgumentCaptor.forClass(MissionCheckCompletePayload.class);
        verify(eventPublisher).publish(any(), eq("MISSION_CHECK_COMPLETE"), captor.capture());
        assertThat(captor.getValue().checkedCount()).isEqualTo(1);
        assertThat(captor.getValue().totalCount()).isEqualTo(3);
    }

    // ── all checked → ending ──────────────────────────────────────────────────

    @Test
    void checkComplete_lastPlayer_transitionsToEnding() {
        Session s = missionSessionWith3Players();
        s.getPlayers().get(0).acknowledgeMission(Instant.now());
        s.getPlayers().get(1).acknowledgeMission(Instant.now());
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.checkComplete(SESSION_ID, playerCId);

        assertThat(s.getState()).isEqualTo("ending");
    }

    @Test
    void checkComplete_lastPlayer_broadcastsCheckCompleteThenStateChanged() {
        Session s = missionSessionWith3Players();
        s.getPlayers().get(0).acknowledgeMission(Instant.now());
        s.getPlayers().get(1).acknowledgeMission(Instant.now());
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.checkComplete(SESSION_ID, playerCId);

        InOrder order = inOrder(eventPublisher);
        order.verify(eventPublisher).publish(any(), eq("MISSION_CHECK_COMPLETE"), any());
        order.verify(eventPublisher).publish(any(), eq("SESSION_STATE_CHANGED"),
            argThat(p -> p instanceof SessionStateChangedPayload sc && "ending".equals(sc.state())));
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void checkComplete_idempotent_doubleCheckByNonLastPlayer_doesNotReBroadcast() {
        Session s = missionSessionWith3Players();
        when(sessionRepo.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(s));

        service.checkComplete(SESSION_ID, playerAId);
        reset(eventPublisher);
        service.checkComplete(SESSION_ID, playerAId);

        verify(eventPublisher, never()).publish(any(), eq("MISSION_CHECK_COMPLETE"), any());
        verify(eventPublisher, never()).publish(any(), eq("SESSION_STATE_CHANGED"), any());
    }
}
