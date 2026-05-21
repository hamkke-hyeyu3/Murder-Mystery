package com.murdermystery.session;

import com.murdermystery.ws.event.SessionStateChangedPayload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared helper for transitioning a session to the 'ending' state.
 * Used by both MissionService (all-checked path) and ForceProgressService.
 * Caller must invoke transitionToEnding() inside a transaction, then
 * broadcastEndingTransition() after commit.
 */
@Component
public class MissionEndingHelper {

    private final SessionRepository sessionRepository;
    private final SessionEventPublisher eventPublisher;

    public MissionEndingHelper(SessionRepository sessionRepository, SessionEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    public void transitionToEnding(Session session) {
        session.setState("ending");
        sessionRepository.saveAndFlush(session);
    }

    public void broadcastEndingTransition(String sessionId) {
        eventPublisher.publish(sessionId, "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("ending", null));
    }
}
