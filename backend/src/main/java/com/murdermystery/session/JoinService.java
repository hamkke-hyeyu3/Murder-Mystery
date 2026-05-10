package com.murdermystery.session;

import com.murdermystery.scenario.ScenarioRepository;
import com.murdermystery.ws.event.LobbyCountChangedPayload;
import com.murdermystery.ws.event.PlayerJoinedPayload;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JoinService {

    private record JoinBroadcastBundle(JoinResponse response, Optional<LobbyCountChangedPayload> count) {}

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final ScenarioRepository scenarioRepository;

    public JoinService(
        SessionRepository sessionRepository,
        PlayerRepository playerRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        ScenarioRepository scenarioRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.playerRepository = playerRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.scenarioRepository = scenarioRepository;
    }

    public JoinResponse join(String inviteCode, String rawNickname, UUID deviceId) {
        Session session = loadJoinableSession(inviteCode);
        String nickname = Nicknames.validate(rawNickname);

        JoinBroadcastBundle bundle;
        try {
            // Idempotent check and new-join insert run in the same transaction to close the
            // sequential race window. The partial unique index on (session_id, device_id) guards
            // against truly concurrent requests that slip past the in-transaction check.
            bundle = transactionTemplate.execute(status -> tryJoin(session, nickname, deviceId));
        } catch (DataIntegrityViolationException ex) {
            return recoverFromConflict(session, nickname, deviceId, ex);
        }

        // Broadcast after transaction commits — skipped for idempotent re-joins (count is empty)
        bundle.count().ifPresent(count -> broadcastJoin(bundle.response(), count));
        return bundle.response();
    }

    private Session loadJoinableSession(String inviteCode) {
        Session session = sessionRepository.findByInviteCode(inviteCode)
            .orElseThrow(() -> new InviteCodeNotFoundException(inviteCode));
        if (!"lobby".equals(session.getPhase())) {
            throw new SessionNotJoinableException(session.getPhase());
        }
        return session;
    }

    private JoinBroadcastBundle tryJoin(Session session, String nickname, UUID deviceId) {
        if (deviceId != null) {
            Optional<Player> existing = playerRepository.findBySessionIdAndDeviceId(session.getId(), deviceId);
            if (existing.isPresent()) {
                Player p = existing.get();
                List<PlayerSummary> players = toSummaries(session.getPlayers());
                return new JoinBroadcastBundle(
                    new JoinResponse(session.getId().toString(), session.getInviteCode(),
                        session.getScenarioId(), session.getPhase(),
                        p.getNickname(), p.getId().toString(), players),
                    Optional.empty()
                );
            }
        }

        Player player = new Player(nickname, false, deviceId);
        session.addPlayer(player);
        sessionRepository.saveAndFlush(session);
        List<PlayerSummary> players = toSummaries(session.getPlayers());
        JoinResponse response = new JoinResponse(
            session.getId().toString(), session.getInviteCode(),
            session.getScenarioId(), session.getPhase(),
            player.getNickname(), player.getId().toString(), players
        );
        int required = scenarioRepository.findById(session.getScenarioId())
            .orElseThrow(() -> new IllegalStateException("scenario not found: " + session.getScenarioId()))
            .characters().size();
        return new JoinBroadcastBundle(response, Optional.of(new LobbyCountChangedPayload(players.size(), required)));
    }

    private JoinResponse recoverFromConflict(Session session, String nickname, UUID deviceId,
                                              DataIntegrityViolationException ex) {
        // Transaction rolled back. All re-queries run in fresh transactions (safe after abort).
        // Priority 1: same device won the race → idempotent response, no broadcast.
        if (deviceId != null) {
            Optional<Player> existing = playerRepository.findBySessionIdAndDeviceId(session.getId(), deviceId);
            if (existing.isPresent()) {
                Player p = existing.get();
                List<PlayerSummary> players = sessionRepository.findById(session.getId())
                    .map(s -> toSummaries(s.getPlayers()))
                    .orElse(List.of());
                return new JoinResponse(
                    session.getId().toString(), session.getInviteCode(),
                    session.getScenarioId(), session.getPhase(),
                    p.getNickname(), p.getId().toString(), players
                );
            }
        }
        // Priority 2: different device took the same nickname → 409.
        if (playerRepository.existsBySessionIdAndNickname(session.getId(), nickname)) {
            throw new NicknameTakenException(nickname);
        }
        throw ex;
    }

    private void broadcastJoin(JoinResponse response, LobbyCountChangedPayload count) {
        var playerJoined = new PlayerJoinedPayload(response.playerId(), response.nickname(), false);
        eventPublisher.publish(response.sessionId(), "PLAYER_JOINED", playerJoined);
        eventPublisher.publish(response.sessionId(), "LOBBY_COUNT_CHANGED", count);
    }

    private static List<PlayerSummary> toSummaries(List<Player> players) {
        return players.stream()
            .map(p -> new PlayerSummary(p.getId().toString(), p.getNickname(), p.isHost()))
            .toList();
    }
}
