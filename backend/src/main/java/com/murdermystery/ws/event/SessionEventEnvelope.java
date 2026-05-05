package com.murdermystery.ws.event;

import java.time.Instant;

public record SessionEventEnvelope<T>(String type, Instant occurredAt, String sessionId, T payload) {}
