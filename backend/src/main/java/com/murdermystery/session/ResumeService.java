package com.murdermystery.session;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeService {

    private final PlayerRepository playerRepository;

    public ResumeService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ResumeResponse> findActiveSession(UUID deviceId) {
        return playerRepository
            .findFirstByDeviceIdAndSession_PhaseNotOrderByJoinedAtDesc(deviceId, "ended")
            .map(player -> {
                Session session = player.getSession();
                List<PlayerSummary> players = session.getPlayers().stream()
                    .map(p -> new PlayerSummary(p.getId().toString(), p.getNickname(), p.isHost()))
                    .toList();
                return new ResumeResponse(
                    session.getId().toString(),
                    session.getInviteCode(),
                    session.getScenarioId(),
                    session.getPhase(),
                    player.getNickname(),
                    player.getId().toString(),
                    player.isHost(),
                    players
                );
            });
    }
}
