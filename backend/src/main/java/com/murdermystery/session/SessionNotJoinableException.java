package com.murdermystery.session;

public class SessionNotJoinableException extends RuntimeException {
    public SessionNotJoinableException(String phase) {
        super("session not in lobby (phase=" + phase + ")");
    }
}
