package com.murdermystery.ws;

public record ItemExchangeRequest(
    String partnerPlayerId,
    String requesterClueId,
    String partnerClueId
) {}
