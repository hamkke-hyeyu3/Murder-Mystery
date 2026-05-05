package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StompAuthInterceptorTest {

    private PlayerRepository playerRepository;
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        playerRepository = mock(PlayerRepository.class);
        interceptor = new StompAuthInterceptor(playerRepository);
    }

    private Message<byte[]> connectMessage(Map<String, String> headers) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        headers.forEach(accessor::addNativeHeader);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private StompPrincipal principalFrom(Message<?> result) {
        StompHeaderAccessor ra = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(ra).isNotNull();
        assertThat(ra.getUser()).isInstanceOf(StompPrincipal.class);
        return (StompPrincipal) ra.getUser();
    }

    @Test
    void validPlayer_setsAuthenticatedPrincipal() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                playerId, "alice", "123456")).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(MessageChannel.class));

        StompPrincipal stomp = principalFrom(result);
        assertThat(stomp.inviteCode()).isEqualTo("123456");
        assertThat(stomp.playerId()).isEqualTo(playerId.toString());
        assertThat(stomp.isAuthenticated()).isTrue();
    }

    @Test
    void missingHeaders_setsAnonPrincipal() {
        Message<?> result = interceptor.preSend(
            connectMessage(Map.of()), mock(MessageChannel.class));

        assertThat(principalFrom(result).isAuthenticated()).isFalse();
        verify(playerRepository, never()).existsByIdAndNicknameAndSession_InviteCode(any(), any(), any());
    }

    @Test
    void unknownInviteCode_setsAnonPrincipal() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(any(), any(), any()))
            .thenReturn(false);

        Message<?> result = interceptor.preSend(connectMessage(Map.of(
            "X-Invite-Code", "999999",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(MessageChannel.class));

        assertThat(principalFrom(result).isAuthenticated()).isFalse();
    }

    @Test
    void playerIdMismatchesNickname_setsAnonPrincipal() {
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                eq(playerId), eq("wrongNickname"), any())).thenReturn(false);

        Message<?> result = interceptor.preSend(connectMessage(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "wrongNickname",
            "X-Player-Id", playerId.toString()
        )), mock(MessageChannel.class));

        assertThat(principalFrom(result).isAuthenticated()).isFalse();
    }

    @Test
    void validPlayerInInProgressPhase_stillSetsAuthenticatedPrincipal() {
        // Phase is NOT validated at STOMP CONNECT — action-layer validates
        UUID playerId = UUID.randomUUID();
        when(playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                playerId, "alice", "123456")).thenReturn(true);

        Message<?> result = interceptor.preSend(connectMessage(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", playerId.toString()
        )), mock(MessageChannel.class));

        assertThat(principalFrom(result).isAuthenticated()).isTrue();
    }

    @Test
    void invalidPlayerIdFormat_setsAnonPrincipal() {
        Message<?> result = interceptor.preSend(connectMessage(Map.of(
            "X-Invite-Code", "123456",
            "X-Nickname", "alice",
            "X-Player-Id", "not-a-valid-uuid"
        )), mock(MessageChannel.class));

        assertThat(principalFrom(result).isAuthenticated()).isFalse();
        verify(playerRepository, never()).existsByIdAndNicknameAndSession_InviteCode(any(), any(), any());
    }

    @Test
    void nonConnectCommand_passesThroughWithoutValidation() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(
            new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        assertThat(result).isSameAs(message);
        verify(playerRepository, never()).existsByIdAndNicknameAndSession_InviteCode(any(), any(), any());
    }
}
