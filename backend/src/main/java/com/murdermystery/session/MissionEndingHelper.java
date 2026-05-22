package com.murdermystery.session;

import org.springframework.stereotype.Component;

/**
 * Shared helper for transitioning a session to the 'ending' state.
 * Used by both MissionService (all-checked path) and ForceProgressService.
 * Caller must invoke transitionToEnding() inside a transaction, then
 * broadcastEndingTransition() after commit.
 */
@Component
public class MissionEndingHelper {

    private final SessionRepository sessionRepository;
    private final EndingService endingService;

    public MissionEndingHelper(SessionRepository sessionRepository, EndingService endingService) {
        this.sessionRepository = sessionRepository;
        this.endingService = endingService;
    }

    public void transitionToEnding(Session session) {
        session.setState("ending");
        sessionRepository.saveAndFlush(session);
    }

    public void broadcastEndingTransition(String sessionId) {
        endingService.startEnding(sessionId);
    }
}
