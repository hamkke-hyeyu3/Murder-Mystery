package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.ItemService;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.RoundTurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class SessionStompControllerTest {

    private LeaveService leaveService;
    private RoundTurnService roundTurnService;
    private SessionStompController controller;

    @BeforeEach
    void setUp() {
        leaveService = mock(LeaveService.class);
        roundTurnService = mock(RoundTurnService.class);
        controller = new SessionStompController(leaveService, roundTurnService, mock(ItemService.class));
    }

    @Test
    void leave_delegatesSessionIdAndPrincipalToLeaveService() {
        Principal principal = new StompPrincipal("123456:some-player-uuid");
        controller.leave("session-id", principal);
        verify(leaveService).leave("session-id", principal);
    }

    @Test
    void itemExchange_malformedUuid_silentlyIgnored() {
        // malformed UUID in payload must not kill the STOMP session
        StompPrincipal principal = new StompPrincipal("ABCDEF:00000000-0000-0000-0000-000000000001");
        assertThatCode(() -> controller.itemExchange(
            "00000000-0000-0000-0000-000000000002",
            new ItemExchangeRequest("not-a-uuid",
                "00000000-0000-0000-0000-000000000003",
                "00000000-0000-0000-0000-000000000004"),
            principal)
        ).doesNotThrowAnyException();
    }
}
