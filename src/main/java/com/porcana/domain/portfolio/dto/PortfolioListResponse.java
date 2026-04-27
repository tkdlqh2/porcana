package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioListResponse {
    private final String portfolioId;
    private final String name;
    private final String status;
    private final Boolean isMain;
    private final Double totalReturnPct;
    @Schema(description = "시드 설정(Baseline) 존재 여부")
    private final Boolean hasBaseline;
    private final LocalDate createdAt;
    private final List<TopAsset> topAssets;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param isMain Whether this portfolio is the main portfolio
     * @param totalReturnPct Total return percentage
     * @param hasBaseline Whether holding baseline exists
     * @param topAssets Top 3 assets by weight
     * @return PortfolioListResponse
     */
    public static PortfolioListResponse from(Portfolio portfolio, boolean isMain, Double totalReturnPct,
                                             boolean hasBaseline, List<TopAsset> topAssets) {
        return PortfolioListResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .isMain(isMain)
                .totalReturnPct(totalReturnPct)
                .hasBaseline(hasBaseline)
                .createdAt(portfolio.getCreatedAt().toLocalDate())
                .topAssets(topAssets)
                .build();
    }

    /**
         * Top asset information for portfolio list
         */
        @Builder
        public record TopAsset(UUID assetId, String symbol, String name, String imageUrl, BigDecimal weight) {
            public static TopAsset from(UUID assetId, String symbol, String name, String imageUrl, BigDecimal weight) {
                return TopAsset.builder()
                        .assetId(assetId)
                        .symbol(symbol)
                        .name(name)
                        .imageUrl(imageUrl)
                        .weight(weight)
                        .build();
            }
        }
}
