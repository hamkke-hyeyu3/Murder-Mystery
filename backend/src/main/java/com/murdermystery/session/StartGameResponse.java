package com.murdermystery.session;

import java.util.List;
import java.util.UUID;

public record StartGameResponse(UUID sessionId, String phase, String state, List<String> turnOrder) {}
