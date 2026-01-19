package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class PortfolioPerformanceResponse {
    private final String portfolioId;
    private final String range;
    private final List<PerformancePoint> points;

    @Getter
    @Builder
    public static class PerformancePoint {
        private final LocalDate date;
        private final Double value;
    }
}