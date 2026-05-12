package com.murdermystery.ws.event;

public record RoundEndedPayload(int roundNumber, String reason, long endedAt) {}
