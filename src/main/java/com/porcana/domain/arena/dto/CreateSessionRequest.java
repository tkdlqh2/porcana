package com.porcana.domain.arena.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSessionRequest(
        @NotNull(message = "Portfolio ID is required")
        UUID portfolioId
) {
}
