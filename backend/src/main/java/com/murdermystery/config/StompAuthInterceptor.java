package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final PlayerRepository playerRepository;

    public StompAuthInterceptor(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String inviteCode = accessor.getFirstNativeHeader("X-Invite-Code");
        String nickname   = accessor.getFirstNativeHeader("X-Nickname");
        String playerId   = accessor.getFirstNativeHeader("X-Player-Id");

        if (inviteCode != null && nickname != null && playerId != null) {
            try {
                UUID playerUUID = UUID.fromString(playerId);
                if (playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                        playerUUID, nickname, inviteCode)) {
                    accessor.setUser(new StompPrincipal(inviteCode + ":" + playerId));
                    return message;
                }
            } catch (IllegalArgumentException ignored) {
                // invalid UUID format → anonymous
            }
        }

        accessor.setUser(new StompPrincipal("anon-" + UUID.randomUUID()));
        return message;
    }
}
