package com.murdermystery.session;

import com.murdermystery.scenario.ScenarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int INVITE_CODE_RETRY_LIMIT = 5;

    private final ScenarioRepository scenarioRepository;
    private final SessionRepository sessionRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final TransactionTemplate transactionTemplate;

    public SessionService(
        ScenarioRepository scenarioRepository,
        SessionRepository sessionRepository,
        InviteCodeGenerator inviteCodeGenerator,
        TransactionTemplate transactionTemplate
    ) {
        this.scenarioRepository = scenarioRepository;
        this.sessionRepository = sessionRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    public SessionViewResponse getSession(java.util.UUID sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId.toString()));
        int required = scenarioRepository.findById(session.getScenarioId())
            .orElseThrow(() -> new IllegalStateException("scenario not found: " + session.getScenarioId()))
            .characters().size();
        java.util.List<PlayerSummary> players = session.getPlayers().stream()
            .map(p -> new PlayerSummary(p.getId().toString(), p.getNickname(), p.isHost()))
            .toList();
        return new SessionViewResponse(
            session.getId().toString(),
            session.getInviteCode(),
            session.getScenarioId(),
            session.getPhase(),
            required,
            players.size(),
            players
        );
    }

    public CreateSessionResponse createSession(String scenarioId, String hostNickname) {
        scenarioRepository.findById(scenarioId)
            .orElseThrow(() -> new IllegalArgumentException("unknown scenario: " + scenarioId));

        String trimmed = validateNickname(hostNickname);

        for (int attempt = 0; attempt < INVITE_CODE_RETRY_LIMIT; attempt++) {
            String code = inviteCodeGenerator.next();
            try {
                return transactionTemplate.execute(status -> {
                    Session session = new Session(code, scenarioId);
                    Player host = new Player(trimmed, true);
                    session.addPlayer(host);
                    sessionRepository.saveAndFlush(session);
                    return new CreateSessionResponse(
                        session.getId().toString(),
                        session.getInviteCode(),
                        session.getScenarioId(),
                        host.getNickname(),
                        session.getPhase(),
                        host.getId().toString()
                    );
                });
            } catch (DataIntegrityViolationException dup) {
                log.debug("invite code collision on attempt {}: {}", attempt + 1, code);
            }
        }

        log.error("invite code exhausted after {} attempts", INVITE_CODE_RETRY_LIMIT);
        throw new IllegalStateException("invite code exhausted");
    }

    private String validateNickname(String raw) {
        if (raw == null) throw new IllegalArgumentException("nickname must not be null");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("nickname must not be blank");
        if (trimmed.length() > 20) throw new IllegalArgumentException("nickname too long (max 20)");
        if (trimmed.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("nickname contains invalid characters");
        return trimmed;
    }
}
