package com.murdermystery.ws.event;

public record PlayerJoinedPayload(String playerId, String nickname, boolean isHost) {}
