package com.murdermystery.session;

public class NotHostException extends RuntimeException {
    public NotHostException() {
        super("only the host can start the game");
    }
}
