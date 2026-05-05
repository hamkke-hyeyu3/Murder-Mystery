package com.murdermystery.config;

import com.murdermystery.session.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    private final StompAuthInterceptor authInterceptor =
        new StompAuthInterceptor(mock(PlayerRepository.class));
    private final WebSocketConfig config =
        new WebSocketConfig(new StompHandshakeHandler(), authInterceptor);

    @Test
    void configureMessageBroker_enablesSimpleBrokerOnTopicAndQueue() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        when(registry.enableSimpleBroker(anyString(), anyString())).thenReturn(null);
        when(registry.setApplicationDestinationPrefixes(anyString())).thenReturn(registry);
        when(registry.setUserDestinationPrefix(anyString())).thenReturn(registry);

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    void configureClientInboundChannel_registersStompAuthInterceptor() {
        ChannelRegistration registration = mock(ChannelRegistration.class);
        when(registration.interceptors(any())).thenReturn(registration);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(authInterceptor);
    }

    @Test
    void registerStompEndpoints_registersWsEndpointWithSockJs() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint(anyString())).thenReturn(registration);
        when(registration.setHandshakeHandler(any())).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(anyString())).thenReturn(registration);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registration).withSockJS();
    }
}
