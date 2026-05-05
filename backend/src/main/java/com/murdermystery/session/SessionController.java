package com.murdermystery.session;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final JoinService joinService;

    public SessionController(SessionService sessionService, JoinService joinService) {
        this.sessionService = sessionService;
        this.joinService = joinService;
    }

    @PostMapping
    public CreateSessionResponse create(@RequestBody @Valid CreateSessionRequest req) {
        return sessionService.createSession(req.scenarioId(), req.hostNickname());
    }

    @PostMapping("/{inviteCode}/join")
    public JoinResponse join(@PathVariable String inviteCode,
                             @RequestBody @Valid JoinRequest req) {
        return joinService.join(inviteCode, req.nickname());
    }
}
