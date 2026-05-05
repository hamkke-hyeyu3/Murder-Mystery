package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StompHandshakeHandlerTest {

    private PlayerRepository playerRepository;
    private StompHandshakeHandler handler;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        handler = new StompHandshakeHandler(playerRepository);
    }

    private ServerHttpRequest requestWith(Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::add);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(httpHeaders);
        return request;
    }

    @Test
    void validPlayer_returnsAuthenticatedStompPrincipal() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                playerId, "alice", "123456")).thenReturn(true);

        Principal principal = handler.determineUser(requestWith(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(WebSocketHandler.class), Map.of());

        assertThat(principal).isInstanceOf(StompPrincipal.class);
        StompPrincipal stomp = (StompPrincipal) principal;
        assertThat(stomp.inviteCode()).isEqualTo("123456");
        assertThat(stomp.playerId()).isEqualTo(playerId.toString());
        assertThat(stomp.isAuthenticated()).isTrue();
    }

    @Test
    void missingHeaders_returnsAnon() {
        Principal principal = handler.determineUser(
            requestWith(Map.of()), mock(WebSocketHandler.class), Map.of());

        assertThat(((StompPrincipal) principal).isAuthenticated()).isFalse();
        verify(playerRepository, never()).existsByIdAndNicknameAndSession_InviteCode(any(), any(), any());
    }

    @Test
    void unknownInviteCode_returnsAnon() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(any(), any(), any()))
            .thenReturn(false);

        Principal principal = handler.determineUser(requestWith(Map.of(
            "X-Invite-Code", "999999",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(WebSocketHandler.class), Map.of());

        assertThat(((StompPrincipal) principal).isAuthenticated()).isFalse();
    }

    @Test
    void playerIdMismatchesNickname_returnsAnon() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                eq(playerId), eq("wrongNickname"), any())).thenReturn(false);

        Principal principal = handler.determineUser(requestWith(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "wrongNickname",
            "X-Player-Id", playerId.toString()
        )), mock(WebSocketHandler.class), Map.of());

        assertThat(((StompPrincipal) principal).isAuthenticated()).isFalse();
    }

    @Test
    void validPlayerInInProgressPhase_stillReturnsAuthenticated() {
        // Phase is NOT validated at handshake — action-layer validates
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                playerId, "alice", "123456")).thenReturn(true);

        Principal principal = handler.determineUser(requestWith(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(WebSocketHandler.class), Map.of());

        assertThat(((StompPrincipal) principal).isAuthenticated()).isTrue();
    }

    @Test
    void invalidPlayerIdFormat_returnsAnon() {
        Principal principal = handler.determineUser(requestWith(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", "not-a-valid-uuid"
        )), mock(WebSocketHandler.class), Map.of());

        assertThat(((StompPrincipal) principal).isAuthenticated()).isFalse();
        verify(playerRepository, never()).existsByIdAndNicknameAndSession_InviteCode(any(), any(), any());
    }
}
