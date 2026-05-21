package com.murdermystery.session;

import com.murdermystery.ws.event.MissionCheckCompletePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class MissionService {

    private static final Logger log = LoggerFactory.getLogger(MissionService.class);

    private final SessionRepository sessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final SessionEventPublisher eventPublisher;
    private final MissionEndingHelper endingHelper;
    private final ForceProgressService forceProgressService;

    public MissionService(
        SessionRepository sessionRepository,
        TransactionTemplate transactionTemplate,
        SessionEventPublisher eventPublisher,
        MissionEndingHelper endingHelper,
        ForceProgressService forceProgressService
    ) {
        this.sessionRepository = sessionRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.endingHelper = endingHelper;
        this.forceProgressService = forceProgressService;
    }

    public void checkComplete(UUID sessionId, UUID playerId) {
        CheckResult result = transactionTemplate.execute(status -> {
            Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));

            if (!"in_progress".equals(session.getPhase()) || !"mission".equals(session.getState())) {
                throw new MissionPhaseRequiredException();
            }

            Player player = session.getPlayers().stream()
                .filter(p -> playerId.equals(p.getId()))
                .findFirst()
                .orElseThrow(PlayerNotInSessionException::new);

            boolean isNewCheck = player.getMissionCheckedAt() == null;
            player.acknowledgeMission(Instant.now());

            long checkedCount = session.getPlayers().stream()
                .filter(p -> p.getMissionCheckedAt() != null)
                .count();
            int total = session.getPlayers().size();
            boolean allChecked = checkedCount == total;

            if (allChecked) {
                endingHelper.transitionToEnding(session);
            } else {
                sessionRepository.saveAndFlush(session);
            }

            return new CheckResult(
                player.getId().toString(),
                player.getNickname(),
                (int) checkedCount,
                total,
                allChecked,
                isNewCheck
            );
        });

        if (!result.isNewCheck()) {
            log.debug("checkComplete idempotent: player {} already checked mission in session {}", playerId, sessionId);
            return;
        }

        if (result.checkedCount() == 1) {
            forceProgressService.scheduleEvaluation(sessionId);
        }

        eventPublisher.publish(
            sessionId.toString(),
            "MISSION_CHECK_COMPLETE",
            new MissionCheckCompletePayload(result.playerId(), result.nickname(), result.checkedCount(), result.totalCount())
        );

        if (result.allChecked()) {
            forceProgressService.cancel(sessionId);
            endingHelper.broadcastEndingTransition(sessionId.toString());
        }
    }

    private record CheckResult(
        String playerId,
        String nickname,
        int checkedCount,
        int totalCount,
        boolean allChecked,
        boolean isNewCheck
    ) {}
}
