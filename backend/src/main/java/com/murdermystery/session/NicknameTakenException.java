package com.murdermystery.session;

public class NicknameTakenException extends RuntimeException {
    public NicknameTakenException(String nickname) {
        super("nickname already taken: " + nickname);
    }
}
