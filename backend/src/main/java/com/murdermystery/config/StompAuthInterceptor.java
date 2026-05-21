package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import com.murdermystery.session.SessionRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final PlayerRepository playerRepository;
    private final SessionRepository sessionRepository;

    public StompAuthInterceptor(PlayerRepository playerRepository, SessionRepository sessionRepository) {
        this.playerRepository = playerRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(message, accessor);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            touchLastSeen(accessor);
        }

        return message;
    }

    private void touchLastSeen(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        try {
            playerRepository.touchLastSeen(UUID.fromString(sp.playerId()), Instant.now());
        } catch (Exception ignored) {
            // best-effort: never break the message pipeline
        }
    }

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
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

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        // Guard only session-scoped topic broadcasts; all other destinations pass through.
        if (destination == null || !destination.startsWith("/topic/session/")) return message;

        // Extract sessionId from /topic/session/{sessionId}/...
        String[] parts = destination.split("/", 5);
        // parts: ["", "topic", "session", "{sessionId}", ...]
        if (parts.length < 4) return message;

        try {
            UUID sessionId = UUID.fromString(parts[3]);
            Object user = accessor.getUser();
            if (!(user instanceof StompPrincipal principal) || !principal.isAuthenticated()) return null;
            String principalInviteCode = principal.inviteCode();
            boolean allowed = sessionRepository.findById(sessionId)
                .map(s -> s.getInviteCode().equals(principalInviteCode))
                .orElse(false);
            return allowed ? message : null;
        } catch (IllegalArgumentException e) {
            // malformed UUID in destination — drop
            return null;
        }
    }
}
