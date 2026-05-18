package com.murdermystery.ws.event;

import java.util.List;

public record PrivateTalkEndedPayload(
    String requestId,
    List<PrivateTalkStartedPayload.Participant> participants,
    String endReason,
    long endedAt
) {}
