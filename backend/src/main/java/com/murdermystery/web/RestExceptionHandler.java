package com.murdermystery.web;

import com.murdermystery.session.LobbyCountMismatchException;
import com.murdermystery.session.NicknameTakenException;
import com.murdermystery.session.NotHostException;
import com.murdermystery.session.PlayerNotInSessionException;
import com.murdermystery.session.SessionAlreadyStartedException;
import com.murdermystery.session.SessionNotFoundException;
import com.murdermystery.session.SessionNotJoinableException;
import com.murdermystery.session.TutorialPhaseRequiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail(fields.isEmpty() ? "Validation failed" : fields);
        return detail;
    }

    @ExceptionHandler(NicknameTakenException.class)
    public ProblemDetail handleNicknameTaken(NicknameTakenException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("nickname already taken");
        return detail;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ProblemDetail handleSessionNotFound(SessionNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail("session not found");
        return detail;
    }

    @ExceptionHandler(SessionNotJoinableException.class)
    public ProblemDetail handleSessionNotJoinable(SessionNotJoinableException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("session not in lobby");
        return detail;
    }

    @ExceptionHandler(NotHostException.class)
    public ProblemDetail handleNotHost(NotHostException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setDetail("host only");
        return detail;
    }

    @ExceptionHandler(SessionAlreadyStartedException.class)
    public ProblemDetail handleSessionAlreadyStarted(SessionAlreadyStartedException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("session already started");
        return detail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("session already started");
        return detail;
    }

    @ExceptionHandler(TutorialPhaseRequiredException.class)
    public ProblemDetail handleTutorialPhaseRequired(TutorialPhaseRequiredException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("tutorial phase required");
        return detail;
    }

    @ExceptionHandler(PlayerNotInSessionException.class)
    public ProblemDetail handlePlayerNotInSession(PlayerNotInSessionException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setDetail("player not in session");
        return detail;
    }

    @ExceptionHandler(LobbyCountMismatchException.class)
    public ProblemDetail handleLobbyCountMismatch(LobbyCountMismatchException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("joined count mismatch");
        detail.setProperty("joined", ex.getJoined());
        detail.setProperty("required", ex.getRequired());
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.error("Internal state error", ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("잠시 후 다시 시도해주세요");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("Internal server error");
        return detail;
    }
}
