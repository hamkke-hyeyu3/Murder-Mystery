package com.murdermystery.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
    @NotBlank @Size(max = 64) String scenarioId,
    @NotBlank @Size(max = 20) String hostNickname
) {}
