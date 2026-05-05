package com.murdermystery.session;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.ws.event.SessionEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class LeaveServiceTest {

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SimpMessagingTemplate messaging;
    private LeaveService service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        service = new LeaveService(sessionRepo, txTemplate, messaging);
    }

    @Test
    void leave_validPrincipal_removesPlayerAndBroadcastsAfterCommit() {
        Session session = new Session("123456", "toy-manor");
        Player bob = new Player("bob", false);
        session.addPlayer(bob);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + bob.getId()));

        InOrder order = inOrder(sessionRepo, messaging);
        order.verify(sessionRepo).saveAndFlush(any());
        order.verify(messaging).convertAndSend(contains("/event"), any(SessionEventEnvelope.class));
        assertThat(session.getPlayers()).doesNotContain(bob);
    }

    @Test
    void leave_unknownInvite_silentlyDrops() {
        when(sessionRepo.findByInviteCode("999999")).thenReturn(Optional.empty());

        service.leave("any-session-id", new StompPrincipal("999999:" + UUID.randomUUID()));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void leave_hostLeave_silentlyDrops_noBroadcast() {
        Session session = new Session("123456", "toy-manor");
        Player alice = new Player("alice", true);
        session.addPlayer(alice);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + alice.getId()));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
        assertThat(session.getPlayers()).contains(alice);
    }

    @Test
    void leave_destinationSessionMismatchesPrincipalInvite_silentlyDrops() {
        Session sessionA = new Session("AAAAAA", "toy-manor");
        Player bob = new Player("bob", false);
        sessionA.addPlayer(bob);
        when(sessionRepo.findByInviteCode("AAAAAA")).thenReturn(Optional.of(sessionA));

        service.leave("completely-different-uuid", new StompPrincipal("AAAAAA:" + bob.getId()));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
        assertThat(sessionA.getPlayers()).contains(bob);
    }

    @Test
    void leave_principalPlayerNotInTargetSession_silentlyDrops() {
        Session session = new Session("123456", "toy-manor");
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + UUID.randomUUID()));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void leave_alreadyRemovedPlayer_idempotentNoOp() {
        Session session = new Session("123456", "toy-manor");
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        UUID playerId = UUID.randomUUID();
        service.leave(session.getId().toString(), new StompPrincipal("123456:" + playerId));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    @Test
    void leave_phaseInProgress_silentlyDrops() {
        Session session = new Session("123456", "toy-manor");
        session.setPhase("in_progress");
        Player bob = new Player("bob", false);
        session.addPlayer(bob);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + bob.getId()));

        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void leave_anonPrincipal_silentlyDrops() {
        service.leave("any-id", new StompPrincipal("anon-" + UUID.randomUUID()));

        verify(sessionRepo, never()).findByInviteCode(any());
        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void leave_nonStompPrincipal_silentlyDrops() {
        Principal plainPrincipal = () -> "someUser";

        service.leave("any-id", plainPrincipal);

        verify(sessionRepo, never()).findByInviteCode(any());
        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
