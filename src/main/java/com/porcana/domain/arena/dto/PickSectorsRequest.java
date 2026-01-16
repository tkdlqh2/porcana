package com.porcana.domain.arena.dto;

import com.porcana.domain.asset.entity.Sector;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PickSectorsRequest(
        @NotEmpty(message = "At least one sector is required")
        @Size(min = 2, max = 3, message = "Must select 2-3 sectors")
        List<Sector> sectors
) {
}
