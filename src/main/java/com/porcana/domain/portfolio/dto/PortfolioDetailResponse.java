package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PortfolioDetailResponse {
    private final String portfolioId;
    private final String name;
    private final String status;
    private final Boolean isMain;
    private final LocalDate startedAt;
    private final Double totalReturnPct;
    private final Double averageRiskLevel;  // 가중 평균 위험도 (1.0 - 5.0)
    private final String diversityLevel;    // 분산도 ("HIGH" | "MEDIUM" | "LOW")
    private final Map<Integer, Double> riskDistribution;  // 위험도별 비중 분포 (1-5 → percentage)
    private final List<PositionInfo> positions;

    @Getter
    @Builder
    public static class PositionInfo {
        private final String assetId;
        private final String ticker;
        private final String name;
        private final Integer currentRiskLevel;  // 1-5 (1: Low, 5: High)
        private final Double weightPct;
        private final Double returnPct;
    }
}