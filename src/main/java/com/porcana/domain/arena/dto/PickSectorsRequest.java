package com.porcana.domain.arena.dto;

import com.porcana.domain.asset.entity.Sector;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PickSectorsRequest(
        @NotNull(message = "Sectors list cannot be null")
        @Size(max = 3, message = "Must select 0-3 sectors")
        List<@NotNull Sector> sectors
) {
}
