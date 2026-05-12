package com.murdermystery.ws;

import java.util.List;

public record ItemSharePartialRequest(String clueId, List<String> recipientPlayerIds) {}
