package com.murdermystery.session;

public class LobbyCountMismatchException extends RuntimeException {
    private final int joined;
    private final int required;

    public LobbyCountMismatchException(int joined, int required) {
        super("joined=" + joined + " required=" + required);
        this.joined = joined;
        this.required = required;
    }

    public int getJoined() { return joined; }
    public int getRequired() { return required; }
}
