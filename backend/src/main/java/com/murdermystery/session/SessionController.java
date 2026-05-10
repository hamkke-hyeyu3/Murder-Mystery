package com.murdermystery.session;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final JoinService joinService;
    private final ResumeService resumeService;
    private final StartGameService startGameService;

    public SessionController(SessionService sessionService, JoinService joinService,
                             ResumeService resumeService, StartGameService startGameService) {
        this.sessionService = sessionService;
        this.joinService = joinService;
        this.resumeService = resumeService;
        this.startGameService = startGameService;
    }

    @PostMapping
    public CreateSessionResponse create(
            @RequestBody @Valid CreateSessionRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        return sessionService.createSession(req.scenarioId(), req.hostNickname(), parseDeviceId(deviceIdHeader));
    }

    @GetMapping("/by-device")
    public ResumeResponse getByDevice(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        UUID deviceId = parseDeviceId(deviceIdHeader);
        if (deviceId == null) throw new IllegalArgumentException("X-Device-Id header required");
        return resumeService.findActiveSession(deviceId)
            .orElseThrow(() -> new SessionNotFoundException("no active session for device"));
    }

    @GetMapping("/{sessionId}")
    public SessionViewResponse get(@PathVariable String sessionId) {
        return sessionService.getSession(UUID.fromString(sessionId));
    }

    @PostMapping("/{sessionId}/start")
    public StartGameResponse start(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        UUID deviceId = parseDeviceId(deviceIdHeader);
        if (deviceId == null) throw new IllegalArgumentException("X-Device-Id header required");
        return startGameService.start(UUID.fromString(sessionId), deviceId);
    }

    @PostMapping("/{inviteCode}/join")
    public JoinResponse join(
            @PathVariable String inviteCode,
            @RequestBody @Valid JoinRequest req,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceIdHeader) {
        return joinService.join(inviteCode, req.nickname(), parseDeviceId(deviceIdHeader));
    }

    private static UUID parseDeviceId(String header) {
        if (header == null) return null;
        try { return UUID.fromString(header); }
        catch (IllegalArgumentException e) { return null; }
    }
}
