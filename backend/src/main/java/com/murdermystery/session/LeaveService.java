package com.murdermystery.session;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.LobbyCountChangedPayload;
import com.murdermystery.ws.event.PlayerLeftPayload;
import com.murdermystery.ws.event.SessionEventEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;
import java.time.Instant;

@Service
public class LeaveService {

    private record LeaveBroadcastBundle(PlayerLeftPayload left, LobbyCountChangedPayload count) {}

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScenarioRepository scenarioRepository;

    public LeaveService(SessionRepository sessionRepository,
                        TransactionTemplate transactionTemplate,
                        SimpMessagingTemplate messagingTemplate,
                        ScenarioRepository scenarioRepository) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.messagingTemplate = messagingTemplate;
        this.scenarioRepository = scenarioRepository;
    }

    public void leave(String sessionId, Principal principal) {
        if (!(principal instanceof StompPrincipal stomp) || !stomp.isAuthenticated()) return;

        String inviteCode = stomp.inviteCode();
        String playerId   = stomp.playerId();
        if (inviteCode == null || playerId == null) return;

        LeaveBroadcastBundle bundle = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByInviteCode(inviteCode).orElse(null);
            if (session == null) return null;
            if (!session.getId().toString().equals(sessionId)) return null;
            if (!"lobby".equals(session.getPhase())) return null;

            Player player = session.getPlayers().stream()
                .filter(p -> p.getId().toString().equals(playerId))
                .findFirst()
                .orElse(null);
            if (player == null) return null;
            if (player.isHost()) return null;

            PlayerLeftPayload leftPayload = new PlayerLeftPayload(player.getId().toString(), player.getNickname());
            session.removePlayer(player);
            sessionRepository.saveAndFlush(session);

            int joined = session.getPlayers().size();
            int required = scenarioRepository.findById(session.getScenarioId())
                .orElseThrow(() -> new IllegalStateException("scenario not found: " + session.getScenarioId()))
                .characters().size();
            return new LeaveBroadcastBundle(leftPayload, new LobbyCountChangedPayload(joined, required));
        });

        if (bundle == null) return;

        messagingTemplate.convertAndSend(
            "/topic/session/" + sessionId + "/event",
            new SessionEventEnvelope<>("PLAYER_LEFT", Instant.now(), sessionId, bundle.left())
        );
        messagingTemplate.convertAndSend(
            "/topic/session/" + sessionId + "/event",
            new SessionEventEnvelope<>("LOBBY_COUNT_CHANGED", Instant.now(), sessionId, bundle.count())
        );
    }
}
