package com.murdermystery.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompHandshakeHandlerTest {

    private final StompHandshakeHandler handler = new StompHandshakeHandler();

    private ServerHttpRequest emptyRequest() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        return request;
    }

    @Test
    void anyRequest_returnsAnonStompPrincipal() {
        Principal principal = handler.determineUser(
            emptyRequest(), mock(WebSocketHandler.class), Map.of());

        assertThat(principal).isInstanceOf(StompPrincipal.class);
        assertThat(((StompPrincipal) principal).isAuthenticated()).isFalse();
    }

    @Test
    void eachCall_returnsDifferentAnonName() {
        Principal p1 = handler.determineUser(emptyRequest(), mock(WebSocketHandler.class), Map.of());
        Principal p2 = handler.determineUser(emptyRequest(), mock(WebSocketHandler.class), Map.of());

        assertThat(p1.getName()).isNotEqualTo(p2.getName());
    }
}
