package com.murdermystery.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class StompHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        // Real auth happens in StompAuthInterceptor on the STOMP CONNECT frame.
        // This sets a non-null placeholder principal for the WebSocket session.
        return new StompPrincipal("anon-" + UUID.randomUUID());
    }
}
