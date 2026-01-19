package com.porcana.domain.asset.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetInMainPortfolioResponse {
    private final Boolean included;
    private final String portfolioId;
    private final Double weightPct;
    private final Double returnPct;

    public static AssetInMainPortfolioResponse notIncluded() {
        return AssetInMainPortfolioResponse.builder()
                .included(false)
                .build();
    }
}