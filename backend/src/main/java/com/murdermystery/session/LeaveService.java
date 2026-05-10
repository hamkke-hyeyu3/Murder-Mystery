package com.murdermystery.session;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.LobbyCountChangedPayload;
import com.murdermystery.ws.event.PlayerLeftPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;

@Service
public class LeaveService {

    private record LeaveBroadcastBundle(PlayerLeftPayload left, LobbyCountChangedPayload count) {}

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final ScenarioRepository scenarioRepository;

    public LeaveService(SessionRepository sessionRepository,
                        TransactionTemplate transactionTemplate,
                        SessionEventPublisher eventPublisher,
                        ScenarioRepository scenarioRepository) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
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

        eventPublisher.publish(sessionId, "PLAYER_LEFT", bundle.left());
        eventPublisher.publish(sessionId, "LOBBY_COUNT_CHANGED", bundle.count());
    }
}
