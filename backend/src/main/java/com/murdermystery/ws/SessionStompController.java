package com.murdermystery.ws;

import com.murdermystery.session.LeaveService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class SessionStompController {

    private final LeaveService leaveService;

    public SessionStompController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @MessageMapping("/session/{sessionId}/leave")
    public void leave(@DestinationVariable String sessionId, Principal principal) {
        leaveService.leave(sessionId, principal);
    }
}
