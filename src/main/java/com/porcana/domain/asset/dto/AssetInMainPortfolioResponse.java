package com.porcana.domain.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "메인 포트폴리오 포함 여부 응답")
public class AssetInMainPortfolioResponse {

    @Schema(description = "포함 여부")
    private final Boolean included;

    @Schema(description = "포트폴리오 ID")
    private final String portfolioId;

    @Schema(description = "포트폴리오 이름")
    private final String portfolioName;

    @Schema(description = "비중 (%)")
    private final Double weightPct;

    @Schema(description = "수익률 (%)")
    private final Double returnPct;

    public static AssetInMainPortfolioResponse notIncluded() {
        return AssetInMainPortfolioResponse.builder()
                .included(false)
                .build();
    }
}