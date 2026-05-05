package com.murdermystery.session;

import com.murdermystery.ws.event.PlayerJoinedPayload;
import com.murdermystery.ws.event.SessionEventEnvelope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class JoinService {

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public JoinService(
        SessionRepository sessionRepository,
        PlayerRepository playerRepository,
        TransactionTemplate transactionTemplate,
        SimpMessagingTemplate messagingTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.transactionTemplate = transactionTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public JoinResponse join(String inviteCode, String rawNickname) {
        Session session = sessionRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new InviteCodeNotFoundException(inviteCode));

        if (!"lobby".equals(session.getPhase())) {
            throw new SessionNotJoinableException(session.getPhase());
        }

        String nickname = validateNickname(rawNickname);

        // Execute in transaction; broadcast happens after commit returns
        JoinResponse response = transactionTemplate.execute(status -> {
            Player player = new Player(nickname, false);
            session.addPlayer(player);
            try {
                sessionRepository.saveAndFlush(session);
            } catch (DataIntegrityViolationException ex) {
                if (playerRepository.existsBySessionIdAndNickname(session.getId(), nickname)) {
                    throw new NicknameTakenException(nickname);
                }
                throw ex;
            }
            List<PlayerSummary> players = session.getPlayers().stream()
                .map(p -> new PlayerSummary(p.getId().toString(), p.getNickname(), p.isHost()))
                .toList();
            return new JoinResponse(
                session.getId().toString(),
                session.getInviteCode(),
                session.getScenarioId(),
                session.getPhase(),
                player.getNickname(),
                player.getId().toString(),
                players
            );
        });

        // Broadcast after transaction commits — never inside the lambda
        var payload = new PlayerJoinedPayload(response.playerId(), response.nickname(), false);
        messagingTemplate.convertAndSend(
            "/topic/session/" + response.sessionId() + "/event",
            new SessionEventEnvelope<>("PLAYER_JOINED", Instant.now(), response.sessionId(), payload)
        );

        return response;
    }

    private static String validateNickname(String raw) {
        if (raw == null) throw new IllegalArgumentException("nickname must not be null");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("nickname must not be blank");
        if (trimmed.length() > 20) throw new IllegalArgumentException("nickname too long (max 20)");
        if (trimmed.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("nickname contains invalid characters");
        return trimmed;
    }
}
