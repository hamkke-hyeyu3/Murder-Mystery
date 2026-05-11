package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.RoundTurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.mockito.Mockito.*;

class SessionStompControllerTest {

    private LeaveService leaveService;
    private RoundTurnService roundTurnService;
    private SessionStompController controller;

    @BeforeEach
    void setUp() {
        leaveService = mock(LeaveService.class);
        roundTurnService = mock(RoundTurnService.class);
        controller = new SessionStompController(leaveService, roundTurnService);
    }

    @Test
    void leave_delegatesSessionIdAndPrincipalToLeaveService() {
        Principal principal = new StompPrincipal("123456:some-player-uuid");
        controller.leave("session-id", principal);
        verify(leaveService).leave("session-id", principal);
    }
}
