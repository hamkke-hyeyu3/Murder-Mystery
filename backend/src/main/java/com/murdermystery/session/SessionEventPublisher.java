package com.murdermystery.session;

import com.murdermystery.ws.event.SessionEventEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SessionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public SessionEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public <P> void publish(String sessionId, String type, P payload) {
        messagingTemplate.convertAndSend(
            "/topic/session/" + sessionId + "/event",
            new SessionEventEnvelope<>(type, Instant.now(), sessionId, payload)
        );
    }
}
