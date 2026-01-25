package com.porcana.domain.home.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class HomeResponse {
    private final boolean hasMainPortfolio;
    private final MainPortfolioInfo mainPortfolio;
    private final List<ChartPoint> chart;
    private final List<PositionInfo> positions;

    @Getter
    @Builder
    public static class MainPortfolioInfo {
        private final String portfolioId;
        private final String name;
        private final LocalDate startedAt;
        private final Double totalReturnPct;
    }

    @Getter
    @Builder
    public static class ChartPoint {
        private final LocalDate date;
        private final Double value;
    }

    @Getter
    @Builder
    public static class PositionInfo {
        private final String assetId;
        private final String ticker;
        private final String name;
        private final String imageUrl;
        private final Double weightPct;
        private final Double returnPct;
    }

    public static HomeResponse noMainPortfolio() {
        return HomeResponse.builder()
                .hasMainPortfolio(false)
                .build();
    }
}