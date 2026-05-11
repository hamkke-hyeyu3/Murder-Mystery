package com.murdermystery.ws;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.session.LeaveService;
import com.murdermystery.session.RoundTurnService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class SessionStompController {

    private final LeaveService leaveService;
    private final RoundTurnService roundTurnService;

    public SessionStompController(LeaveService leaveService, RoundTurnService roundTurnService) {
        this.leaveService = leaveService;
        this.roundTurnService = roundTurnService;
    }

    @MessageMapping("/session/{sessionId}/leave")
    public void leave(@DestinationVariable String sessionId, Principal principal) {
        leaveService.leave(sessionId, principal);
    }

    @MessageMapping("/session/{sessionId}/select-location")
    public void selectLocation(@DestinationVariable String sessionId,
                               @Payload SelectLocationRequest request,
                               Principal principal) {
        if (!(principal instanceof StompPrincipal sp) || !sp.isAuthenticated()) return;
        roundTurnService.selectLocation(
            UUID.fromString(sessionId),
            UUID.fromString(sp.playerId()),
            sp.inviteCode(),
            request.locationId(),
            request.roundNumber(),
            request.turnIndex()
        );
    }
}
