package com.murdermystery.session;

public class SessionAlreadyStartedException extends RuntimeException {
    public SessionAlreadyStartedException() {
        super("session already started");
    }
}
