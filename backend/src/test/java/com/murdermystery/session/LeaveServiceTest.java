package com.murdermystery.session;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.scenario.Scenario;
import com.murdermystery.scenario.ScenarioCharacter;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.LobbyCountChangedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LeaveServiceTest {

    private SessionRepository sessionRepo;
    private TransactionTemplate txTemplate;
    private SessionEventPublisher eventPublisher;
    private ScenarioRepository scenarioRepo;
    private LeaveService service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(SessionRepository.class);
        eventPublisher = mock(SessionEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        scenarioRepo = mock(ScenarioRepository.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        Scenario scenario = mock(Scenario.class);
        when(scenario.characters()).thenReturn(List.of(
            mock(ScenarioCharacter.class), mock(ScenarioCharacter.class), mock(ScenarioCharacter.class)
        ));
        when(scenarioRepo.findById(any())).thenReturn(Optional.of(scenario));
        service = new LeaveService(sessionRepo, txTemplate, eventPublisher, scenarioRepo);
    }

    @Test
    void leave_validPrincipal_removesPlayerAndBroadcastsAfterCommit() {
        Session session = new Session("123456", "toy-manor");
        Player bob = new Player("bob", false);
        session.addPlayer(bob);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + bob.getId()));

        InOrder order = inOrder(sessionRepo, eventPublisher);
        order.verify(sessionRepo).saveAndFlush(any());
        order.verify(eventPublisher, times(2)).publish(any(), any(), any());
        assertThat(session.getPlayers()).doesNotContain(bob);
    }

    @Test
    void leave_validPrincipal_lobbyCountReflectsRemainingPlayers() {
        Session session = new Session("123456", "toy-manor");
        Player alice = new Player("alice", true);
        Player bob = new Player("bob", false);
        session.addPlayer(alice);
        session.addPlayer(bob);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));
        when(sessionRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + bob.getId()));

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(any(), typeCaptor.capture(), payloadCaptor.capture());
        assertThat(typeCaptor.getAllValues().get(0)).isEqualTo("PLAYER_LEFT");
        assertThat(typeCaptor.getAllValues().get(1)).isEqualTo("LOBBY_COUNT_CHANGED");
        LobbyCountChangedPayload count = (LobbyCountChangedPayload) payloadCaptor.getAllValues().get(1);
        assertThat(count.joined()).isEqualTo(1); // only alice remains
        assertThat(count.required()).isEqualTo(3); // mocked toy-manor
    }

    @Test
    void leave_unknownInvite_silentlyDrops() {
        when(sessionRepo.findByInviteCode("999999")).thenReturn(Optional.empty());

        service.leave("any-session-id", new StompPrincipal("999999:" + UUID.randomUUID()));

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void leave_hostLeave_silentlyDrops_noBroadcast() {
        Session session = new Session("123456", "toy-manor");
        Player alice = new Player("alice", true);
        session.addPlayer(alice);
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + alice.getId()));

        verify(eventPublisher, never()).publish(any(), any(), any());
        assertThat(session.getPlayers()).contains(alice);
    }

    @Test
    void leave_destinationSessionMismatchesPrincipalInvite_silentlyDrops() {
        Session sessionA = new Session("AAAAAA", "toy-manor");
        Player bob = new Player("bob", false);
        sessionA.addPlayer(bob);
        when(sessionRepo.findByInviteCode("AAAAAA")).thenReturn(Optional.of(sessionA));

        service.leave("completely-different-uuid", new StompPrincipal("AAAAAA:" + bob.getId()));

        verify(eventPublisher, never()).publish(any(), any(), any());
        assertThat(sessionA.getPlayers()).contains(bob);
    }

    @Test
    void leave_principalPlayerNotInTargetSession_silentlyDrops() {
        Session session = new Session("123456", "toy-manor");
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        service.leave(session.getId().toString(), new StompPrincipal("123456:" + UUID.randomUUID()));

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void leave_alreadyRemovedPlayer_idempotentNoOp() {
        Session session = new Session("123456", "toy-manor");
        when(sessionRepo.findByInviteCode("123456")).thenReturn(Optional.of(session));

        UUID playerId = UUID.randomUUID();
        service.leave(session.getId().toString(), new StompPrincipal("123456:" + playerId));

        verify(eventPublisher, never()).publish(any(), any(), any());
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

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void leave_anonPrincipal_silentlyDrops() {
        service.leave("any-id", new StompPrincipal("anon-" + UUID.randomUUID()));

        verify(sessionRepo, never()).findByInviteCode(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void leave_nonStompPrincipal_silentlyDrops() {
        Principal plainPrincipal = () -> "someUser";

        service.leave("any-id", plainPrincipal);

        verify(sessionRepo, never()).findByInviteCode(any());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
