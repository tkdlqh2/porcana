package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RoundType;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.Asset.Market;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AssetRoundResponse implements RoundResponse {
    private UUID sessionId;
    private Integer round;
    private RoundType roundType;
    private List<AssetOption> assets;

    @Getter
    @Builder
    public static class AssetOption {
        private UUID assetId;
        private String ticker;
        private String name;
        private Sector sector;
        private Market market;
        private AssetClass assetClass;
        private Integer currentRiskLevel;  // 1-5 (1: Low, 5: High)
        private String impactHint;
    }
}
