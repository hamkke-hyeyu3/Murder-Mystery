package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StompHandshakeHandler extends DefaultHandshakeHandler {

    private final PlayerRepository playerRepository;

    public StompHandshakeHandler(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String inviteCode = getHeader(request, "X-Invite-Code");
        String nickname   = getHeader(request, "X-Nickname");
        String playerId   = getHeader(request, "X-Player-Id");

        if (inviteCode != null && nickname != null && playerId != null) {
            try {
                UUID playerUUID = UUID.fromString(playerId);
                if (playerRepository.existsByIdAndNicknameAndSession_InviteCode(
                        playerUUID, nickname, inviteCode)) {
                    return new StompPrincipal(inviteCode + ":" + playerId);
                }
            } catch (IllegalArgumentException ignored) {
                // invalid UUID format → anonymous
            }
        }

        return new StompPrincipal("anon-" + UUID.randomUUID());
    }

    private static String getHeader(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return (values != null && !values.isEmpty()) ? values.getFirst() : null;
    }
}
