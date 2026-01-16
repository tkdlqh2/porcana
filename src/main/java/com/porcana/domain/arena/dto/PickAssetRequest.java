package com.porcana.domain.arena.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PickAssetRequest(
        @NotNull(message = "Asset ID is required")
        UUID pickedAssetId
) {
}
