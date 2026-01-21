package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.entity.RoundType;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PreRoundResponse implements RoundResponse {
    private UUID sessionId;
    private Integer round;  // Always 0 for pre-round
    private RoundType roundType;  // Always PRE_ROUND
    private List<RiskProfileOption> riskProfileOptions;
    private List<SectorOption> sectorOptions;
    private Integer minSectorSelection;
    private Integer maxSectorSelection;

    @Getter
    @Builder
    public static class RiskProfileOption {
        private RiskProfile value;
        private String displayName;
        private String description;
    }

    @Getter
    @Builder
    public static class SectorOption {
        private Sector value;
        private String displayName;
        private Integer assetCount;
    }
}