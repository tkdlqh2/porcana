package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RoundType;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SectorRoundResponse implements RoundResponse {
    private UUID sessionId;
    private Integer round;
    private RoundType roundType;
    private List<SectorOption> sectors;
    private Integer minSelection;
    private Integer maxSelection;

    @Getter
    @Builder
    public static class SectorOption {
        private Sector value;
        private String displayName;
        private Integer assetCount;
    }
}
