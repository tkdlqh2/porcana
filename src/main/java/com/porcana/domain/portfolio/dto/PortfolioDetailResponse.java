package com.porcana.domain.portfolio.dto;

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