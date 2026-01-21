package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.asset.entity.Sector;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PickPreferencesRequest(
        @NotNull(message = "Risk profile is required")
        RiskProfile riskProfile,

        @NotNull(message = "Sectors list cannot be null")
        @Size(max = 3, message = "Must select 0-3 sectors")
        List<@NotNull Sector> sectors
) {
}