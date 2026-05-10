package com.murdermystery.session;

import com.murdermystery.ws.event.SessionStateChangedPayload;
import com.murdermystery.ws.event.TutorialAckedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class TutorialService {

    private static final Logger log = LoggerFactory.getLogger(TutorialService.class);

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final RoundService roundService;

    public TutorialService(
        SessionRepository sessionRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        RoundService roundService
    ) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.roundService = roundService;
    }

    // Called by scheduler after character_assignment delay.
    // If state is already 'tutorial' (e.g., after crash+retry), re-publishes the event.
    public void enterTutorialState(UUID sessionId) {
        Boolean ok = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null || !"in_progress".equals(session.getPhase())) {
                return false;
            }
            if ("tutorial".equals(session.getState())) {
                return true; // already tutorial — re-publish but skip DB write
            }
            if (!"character_assignment".equals(session.getState())) {
                return false;
            }
            session.setState("tutorial");
            sessionRepository.saveAndFlush(session);
            return true;
        });

        if (!Boolean.TRUE.equals(ok)) {
            log.debug("enterTutorialState skipped for session {}", sessionId);
            return;
        }

        eventPublisher.publish(
            sessionId.toString(),
            "SESSION_STATE_CHANGED",
            new SessionStateChangedPayload("tutorial", null)
        );
    }

    public TutorialAckResponse acknowledge(UUID sessionId, UUID requesterDeviceId) {
        AckResult result = transactionTemplate.execute(status -> {
            // Pessimistic lock to serialize concurrent last-ack writes.
            // Without it two players sending the final ack simultaneously both read
            // pre-commit state, compute acked < total, and skip the round transition.
            Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

            if (!"in_progress".equals(session.getPhase()) || !"tutorial".equals(session.getState())) {
                throw new TutorialPhaseRequiredException();
            }

            Player player = session.getPlayers().stream()
                .filter(p -> requesterDeviceId.equals(p.getDeviceId()))
                .findFirst()
                .orElseThrow(PlayerNotInSessionException::new);

            boolean isNewAck = player.getTutorialAckedAt() == null;
            player.acknowledgeTutorial(Instant.now());

            long acked = session.getPlayers().stream()
                .filter(p -> p.getTutorialAckedAt() != null)
                .count();
            int total = session.getPlayers().size();
            boolean allAcked = acked == total;

            if (allAcked) {
                session.setState("round");
            }
            sessionRepository.saveAndFlush(session);

            return new AckResult(player.getId().toString(), player.getNickname(),
                (int) acked, total, allAcked, isNewAck);
        });

        if (result.isNewAck()) {
            eventPublisher.publish(
                sessionId.toString(),
                "TUTORIAL_ACKED",
                new TutorialAckedPayload(result.playerId(), result.nickname(), result.acked(), result.total())
            );
        }
        if (result.allAcked()) {
            eventPublisher.publish(
                sessionId.toString(),
                "SESSION_STATE_CHANGED",
                new SessionStateChangedPayload("round", null)
            );
            roundService.startRound(sessionId, 1);
        }

        return new TutorialAckResponse(result.acked(), result.total(), result.allAcked() ? "round" : "tutorial");
    }

    private record AckResult(
        String playerId, String nickname,
        int acked, int total,
        boolean allAcked, boolean isNewAck
    ) {}
}
