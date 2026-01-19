package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UpdateAssetWeightsResponse {
    private final String portfolioId;
    private final List<AssetWeightInfo> weights;

    @Getter
    @Builder
    public static class AssetWeightInfo {
        private final String assetId;
        private final Double weightPct;
    }
}