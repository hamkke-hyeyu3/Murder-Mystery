package com.murdermystery.ws.event;

import java.util.List;

public record SessionStateChangedPayload(String state, List<String> turnOrder) {}
