package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PortfolioDetailResponse {
    private final String portfolioId;
    private final String name;
    private final String status;
    private final Boolean isMain;
    private final LocalDate startedAt;
    private final Double totalReturnPct;
    private final List<PositionInfo> positions;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param isMain Whether this portfolio is the main portfolio
     * @param totalReturnPct Total return percentage
     * @param positions List of position information
     * @return PortfolioDetailResponse
     */
    public static PortfolioDetailResponse from(Portfolio portfolio, boolean isMain, Double totalReturnPct, List<PositionInfo> positions) {
        return PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .isMain(isMain)
                .startedAt(portfolio.getStartedAt())
                .totalReturnPct(totalReturnPct)
                .positions(positions)
                .build();
    }

    @Getter
    @Builder
    public static class PositionInfo {
        private final String assetId;
        private final String ticker;
        private final String name;
        private final Double weightPct;
        private final Double returnPct;
    }
}