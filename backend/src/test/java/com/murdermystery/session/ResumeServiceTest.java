package com.murdermystery.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeServiceTest {

    private PlayerRepository playerRepo;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        playerRepo = mock(PlayerRepository.class);
        service = new ResumeService(playerRepo);
    }

    @Test
    void findActiveSession_returnsEmpty_whenRepositoryFindsNothing() {
        UUID deviceId = UUID.randomUUID();
        when(playerRepo.findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceId, "ended"))
            .thenReturn(Optional.empty());

        assertThat(service.findActiveSession(deviceId)).isEmpty();
    }

    @Test
    void findActiveSession_passesEndedFilter_toRepository() {
        UUID deviceId = UUID.randomUUID();
        when(playerRepo.findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(any(), any()))
            .thenReturn(Optional.empty());

        service.findActiveSession(deviceId);

        ArgumentCaptor<UUID> deviceCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> phaseCaptor = ArgumentCaptor.forClass(String.class);
        verify(playerRepo)
            .findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceCaptor.capture(), phaseCaptor.capture());
        assertThat(deviceCaptor.getValue()).isEqualTo(deviceId);
        assertThat(phaseCaptor.getValue()).isEqualTo("ended");
    }

    @Test
    void findActiveSession_mapsAllSessionAndPlayerFields() {
        UUID deviceId = UUID.randomUUID();
        Session session = new Session("123456", "toy-manor");
        Player host = new Player("alice", true, deviceId);
        Player guest = new Player("bob", false);
        session.addPlayer(host);
        session.addPlayer(guest);
        when(playerRepo.findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceId, "ended"))
            .thenReturn(Optional.of(host));

        ResumeResponse response = service.findActiveSession(deviceId).orElseThrow();

        assertThat(response.sessionId()).isEqualTo(session.getId().toString());
        assertThat(response.inviteCode()).isEqualTo("123456");
        assertThat(response.scenarioId()).isEqualTo("toy-manor");
        assertThat(response.phase()).isEqualTo("lobby");
        assertThat(response.nickname()).isEqualTo("alice");
        assertThat(response.playerId()).isEqualTo(host.getId().toString());
        assertThat(response.isHost()).isTrue();
    }

    @Test
    void findActiveSession_returnsHostFlagFalse_whenPlayerIsGuest() {
        UUID deviceId = UUID.randomUUID();
        Session session = new Session("654321", "toy-manor");
        Player host = new Player("alice", true);
        Player guest = new Player("bob", false, deviceId);
        session.addPlayer(host);
        session.addPlayer(guest);
        when(playerRepo.findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceId, "ended"))
            .thenReturn(Optional.of(guest));

        ResumeResponse response = service.findActiveSession(deviceId).orElseThrow();

        assertThat(response.nickname()).isEqualTo("bob");
        assertThat(response.isHost()).isFalse();
    }

    @Test
    void findActiveSession_includesAllSessionPlayers_inResponse() {
        UUID deviceId = UUID.randomUUID();
        Session session = new Session("111111", "toy-manor");
        Player alice = new Player("alice", true);
        Player bob = new Player("bob", false, deviceId);
        Player carol = new Player("carol", false);
        session.addPlayer(alice);
        session.addPlayer(bob);
        session.addPlayer(carol);
        when(playerRepo.findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceId, "ended"))
            .thenReturn(Optional.of(bob));

        ResumeResponse response = service.findActiveSession(deviceId).orElseThrow();

        assertThat(response.players()).hasSize(3);
        assertThat(response.players())
            .extracting(PlayerSummary::nickname)
            .containsExactly("alice", "bob", "carol");
        assertThat(response.players())
            .filteredOn(PlayerSummary::isHost)
            .extracting(PlayerSummary::nickname)
            .containsExactly("alice");
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
