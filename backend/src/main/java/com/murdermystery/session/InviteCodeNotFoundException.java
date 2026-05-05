package com.murdermystery.session;

public class InviteCodeNotFoundException extends IllegalArgumentException {
    public InviteCodeNotFoundException(String inviteCode) {
        super("unknown invite code: " + inviteCode);
    }
}
