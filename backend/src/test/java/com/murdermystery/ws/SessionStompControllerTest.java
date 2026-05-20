package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.ItemService;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.PrivateTalkService;
import com.murdermystery.session.RoundTurnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

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
        controller = new SessionStompController(leaveService, roundTurnService, mock(ItemService.class), mock(PrivateTalkService.class), mock(com.murdermystery.session.VoteService.class));
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

    @Test
    void itemShareFull_nullClueId_silentlyIgnored() {
        // null clueId (missing JSON field) must not kill the STOMP session with NPE
        StompPrincipal principal = new StompPrincipal("ABCDEF:00000000-0000-0000-0000-000000000001");
        assertThatCode(() -> controller.itemShareFull(
            "00000000-0000-0000-0000-000000000002",
            new ItemShareFullRequest(null),
            principal)
        ).doesNotThrowAnyException();
    }

    @Test
    void itemSharePartial_malformedOneRecipient_silentlyIgnored() {
        // one malformed UUID in recipientPlayerIds must not kill the STOMP session
        StompPrincipal principal = new StompPrincipal("ABCDEF:00000000-0000-0000-0000-000000000001");
        assertThatCode(() -> controller.itemSharePartial(
            "00000000-0000-0000-0000-000000000002",
            new ItemSharePartialRequest(
                "00000000-0000-0000-0000-000000000003",
                List.of("not-a-uuid", "00000000-0000-0000-0000-000000000004")),
            principal)
        ).doesNotThrowAnyException();
    }

    @Test
    void itemSharePartial_nullRecipients_noOp() {
        StompPrincipal principal = new StompPrincipal("ABCDEF:00000000-0000-0000-0000-000000000001");
        ItemService itemService = mock(ItemService.class);
        SessionStompController ctrl = new SessionStompController(leaveService, roundTurnService, itemService, mock(PrivateTalkService.class), mock(com.murdermystery.session.VoteService.class));

        assertThatCode(() -> ctrl.itemSharePartial(
            "00000000-0000-0000-0000-000000000002",
            new ItemSharePartialRequest("00000000-0000-0000-0000-000000000003", null),
            principal)
        ).doesNotThrowAnyException();
        verify(itemService, never()).sharePartial(any(), any(), any(), any(), any());
    }
}
