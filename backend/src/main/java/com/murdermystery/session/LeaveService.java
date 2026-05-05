package com.murdermystery.session;

import com.murdermystery.config.StompPrincipal;
import com.murdermystery.ws.event.PlayerLeftPayload;
import com.murdermystery.ws.event.SessionEventEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.Principal;
import java.time.Instant;

@Service
public class LeaveService {

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public LeaveService(SessionRepository sessionRepository,
                        TransactionTemplate transactionTemplate,
                        SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public void leave(String sessionId, Principal principal) {
        if (!(principal instanceof StompPrincipal stomp) || !stomp.isAuthenticated()) return;

        String inviteCode = stomp.inviteCode();
        String playerId   = stomp.playerId();
        if (inviteCode == null || playerId == null) return;

        PlayerLeftPayload broadcastPayload = transactionTemplate.execute(status -> {
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

            PlayerLeftPayload payload = new PlayerLeftPayload(player.getId().toString(), player.getNickname());
            session.removePlayer(player);
            sessionRepository.saveAndFlush(session);
            return payload;
        });

        if (broadcastPayload == null) return;

        messagingTemplate.convertAndSend(
            "/topic/session/" + sessionId + "/event",
            new SessionEventEnvelope<>("PLAYER_LEFT", Instant.now(), sessionId, broadcastPayload)
        );
    }
}
