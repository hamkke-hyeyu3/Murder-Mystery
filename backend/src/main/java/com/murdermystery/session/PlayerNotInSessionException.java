package com.murdermystery.session;

public class PlayerNotInSessionException extends RuntimeException {
    public PlayerNotInSessionException() {
        super("player not in session");
    }
}
