package com.murdermystery.session;

public class ForceProgressNotAvailableException extends RuntimeException {
    public ForceProgressNotAvailableException() {
        super("Force progress is not available (not host and NB3 conditions not met)");
    }
}
