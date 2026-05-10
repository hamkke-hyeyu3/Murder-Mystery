package com.murdermystery.ws.event;

public record TutorialAckedPayload(String playerId, String nickname, int acked, int total) {}
