package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioPerformanceResponse {
    private final String portfolioId;
    private final String range;
    private final List<PerformancePoint> points;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param range Range parameter (1M, 3M, 1Y)
     * @param points List of performance points
     * @return PortfolioPerformanceResponse
     */
    public static PortfolioPerformanceResponse from(Portfolio portfolio, String range, List<PerformancePoint> points) {
        return PortfolioPerformanceResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .range(range)
                .points(points)
                .build();
    }

    /**
     * Factory method to create response from portfolio ID
     *
     * @param portfolioId Portfolio ID
     * @param range Range parameter (1M, 3M, 1Y)
     * @param points List of performance points
     * @return PortfolioPerformanceResponse
     */
    public static PortfolioPerformanceResponse from(UUID portfolioId, String range, List<PerformancePoint> points) {
        return PortfolioPerformanceResponse.builder()
                .portfolioId(portfolioId.toString())
                .range(range)
                .points(points)
                .build();
    }

    @Getter
    @Builder
    public static class PerformancePoint {
        private final LocalDate date;
        private final Double value;
    }
}