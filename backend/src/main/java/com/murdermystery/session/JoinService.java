package com.murdermystery.session;

import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.LobbyCountChangedPayload;
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

    private record JoinBroadcastBundle(JoinResponse response, LobbyCountChangedPayload count) {}

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScenarioRepository scenarioRepository;

    public JoinService(
        SessionRepository sessionRepository,
        PlayerRepository playerRepository,
        TransactionTemplate transactionTemplate,
        SimpMessagingTemplate messagingTemplate,
        ScenarioRepository scenarioRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.transactionTemplate = transactionTemplate;
        this.messagingTemplate = messagingTemplate;
        this.scenarioRepository = scenarioRepository;
    }

    public JoinResponse join(String inviteCode, String rawNickname) {
        Session session = sessionRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new InviteCodeNotFoundException(inviteCode));

        if (!"lobby".equals(session.getPhase())) {
            throw new SessionNotJoinableException(session.getPhase());
        }

        String nickname = validateNickname(rawNickname);

        // Execute in transaction; both payloads computed here so broadcast is atomic after commit
        JoinBroadcastBundle bundle = transactionTemplate.execute(status -> {
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
            JoinResponse response = new JoinResponse(
                session.getId().toString(),
                session.getInviteCode(),
                session.getScenarioId(),
                session.getPhase(),
                player.getNickname(),
                player.getId().toString(),
                players
            );
            int joined = players.size();
            int required = scenarioRepository.findById(session.getScenarioId())
                .orElseThrow(() -> new IllegalStateException("scenario not found: " + session.getScenarioId()))
                .characters().size();
            return new JoinBroadcastBundle(response, new LobbyCountChangedPayload(joined, required));
        });

        // Broadcast after transaction commits — never inside the lambda
        var playerJoined = new PlayerJoinedPayload(bundle.response().playerId(), bundle.response().nickname(), false);
        messagingTemplate.convertAndSend(
            "/topic/session/" + bundle.response().sessionId() + "/event",
            new SessionEventEnvelope<>("PLAYER_JOINED", Instant.now(), bundle.response().sessionId(), playerJoined)
        );
        messagingTemplate.convertAndSend(
            "/topic/session/" + bundle.response().sessionId() + "/event",
            new SessionEventEnvelope<>("LOBBY_COUNT_CHANGED", Instant.now(), bundle.response().sessionId(), bundle.count())
        );

        return bundle.response();
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
