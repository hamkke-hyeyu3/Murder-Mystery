package com.murdermystery.config;

import java.security.Principal;

public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }

    public String inviteCode() {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(0, colon) : null;
    }

    public String playerId() {
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : null;
    }

    public boolean isAuthenticated() {
        return !name.startsWith("anon-");
    }
}
