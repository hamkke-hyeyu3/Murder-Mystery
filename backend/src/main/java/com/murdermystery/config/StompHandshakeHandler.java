package com.murdermystery.config;

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

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        List<String> inviteCodes = request.getHeaders().get("X-Invite-Code");
        List<String> nicknames = request.getHeaders().get("X-Nickname");

        if (inviteCodes != null && !inviteCodes.isEmpty()
                && nicknames != null && !nicknames.isEmpty()) {
            String inviteCode = inviteCodes.getFirst();
            String nickname = nicknames.getFirst();
            return new StompPrincipal(inviteCode + ":" + nickname);
        }

        // anonymous fallback — will be replaced with validation in T-03
        return new StompPrincipal("anon-" + UUID.randomUUID());
    }
}
