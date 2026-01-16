package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RiskProfile;
import jakarta.validation.constraints.NotNull;

public record PickRiskProfileRequest(
        @NotNull(message = "Risk profile is required")
        RiskProfile riskProfile
) {
}
