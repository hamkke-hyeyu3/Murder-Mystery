package com.murdermystery.ws.event;

public record ForceProgressAvailablePayload(String scope) {
    public ForceProgressAvailablePayload {
        if (!"host".equals(scope) && !"all".equals(scope)) {
            throw new IllegalArgumentException("scope must be 'host' or 'all'");
        }
    }
}
