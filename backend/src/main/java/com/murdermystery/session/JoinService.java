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
import java.util.Optional;
import java.util.UUID;

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

    public JoinResponse join(String inviteCode, String rawNickname, UUID deviceId) {
        Session session = sessionRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new InviteCodeNotFoundException(inviteCode));

        if (!"lobby".equals(session.getPhase())) {
            throw new SessionNotJoinableException(session.getPhase());
        }

        String nickname = validateNickname(rawNickname);

        // Idempotent check and new-join insert run in the same transaction to close the race window.
        // The partial unique index on (session_id, device_id) is the DB-level safety net for
        // truly concurrent requests that slip past the in-transaction check.
        JoinBroadcastBundle bundle = transactionTemplate.execute(status -> {
            if (deviceId != null) {
                Optional<Player> existing = playerRepository.findBySessionIdAndDeviceId(session.getId(), deviceId);
                if (existing.isPresent()) {
                    Player p = existing.get();
                    List<PlayerSummary> players = session.getPlayers().stream()
                        .map(pl -> new PlayerSummary(pl.getId().toString(), pl.getNickname(), pl.isHost()))
                        .toList();
                    return new JoinBroadcastBundle(
                        new JoinResponse(session.getId().toString(), session.getInviteCode(),
                            session.getScenarioId(), session.getPhase(),
                            p.getNickname(), p.getId().toString(), players),
                        null  // null = idempotent, skip broadcast
                    );
                }
            }

            Player player = new Player(nickname, false, deviceId);
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

        // Broadcast after transaction commits — skipped for idempotent re-joins (count == null)
        if (bundle.count() != null) {
            var playerJoined = new PlayerJoinedPayload(bundle.response().playerId(), bundle.response().nickname(), false);
            messagingTemplate.convertAndSend(
                "/topic/session/" + bundle.response().sessionId() + "/event",
                new SessionEventEnvelope<>("PLAYER_JOINED", Instant.now(), bundle.response().sessionId(), playerJoined)
            );
            messagingTemplate.convertAndSend(
                "/topic/session/" + bundle.response().sessionId() + "/event",
                new SessionEventEnvelope<>("LOBBY_COUNT_CHANGED", Instant.now(), bundle.response().sessionId(), bundle.count())
            );
        }

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
