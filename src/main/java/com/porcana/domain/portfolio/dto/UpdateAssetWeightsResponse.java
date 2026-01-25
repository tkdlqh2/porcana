package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UpdateAssetWeightsResponse {
    private final String portfolioId;
    private final List<AssetWeightInfo> weights;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param weights List of asset weight information
     * @return UpdateAssetWeightsResponse
     */
    public static UpdateAssetWeightsResponse from(Portfolio portfolio, List<AssetWeightInfo> weights) {
        return UpdateAssetWeightsResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .weights(weights)
                .build();
    }

    /**
     * Factory method to create response from portfolio ID
     *
     * @param portfolioId Portfolio ID
     * @param weights List of asset weight information
     * @return UpdateAssetWeightsResponse
     */
    public static UpdateAssetWeightsResponse from(UUID portfolioId, List<AssetWeightInfo> weights) {
        return UpdateAssetWeightsResponse.builder()
                .portfolioId(portfolioId.toString())
                .weights(weights)
                .build();
    }

    @Getter
    @Builder
    public static class AssetWeightInfo {
        private final String assetId;
        private final Double weightPct;
    }
}