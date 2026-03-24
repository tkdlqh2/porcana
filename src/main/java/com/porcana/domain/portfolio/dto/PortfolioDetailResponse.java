package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@Schema(description = "포트폴리오 상세 응답")
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

    @Schema(description = "시드 설정(Baseline) 존재 여부")
    private final Boolean hasBaseline;

    @Schema(description = "Baseline 요약 정보 (hasBaseline=true인 경우)")
    private final BaselineSummary baseline;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param isMain Whether this portfolio is the main portfolio
     * @param totalReturnPct Total return percentage
     * @param positions List of position information
     * @param baselineSummary Baseline summary (nullable)
     * @return PortfolioDetailResponse
     */
    public static PortfolioDetailResponse from(Portfolio portfolio,
                                               boolean isMain,
                                               Double totalReturnPct,
                                               Double averageRiskLevel,
                                               String diversityLevel,
                                               Map<Integer, Double> riskDistribution,
                                               List<PositionInfo> positions,
                                               BaselineSummary baselineSummary
    ) {
        return PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .isMain(isMain)
                .startedAt(portfolio.getStartedAt())
                .totalReturnPct(totalReturnPct)
                .averageRiskLevel(averageRiskLevel)
                .diversityLevel(diversityLevel)
                .riskDistribution(riskDistribution)
                .positions(positions)
                .hasBaseline(baselineSummary != null)
                .baseline(baselineSummary)
                .build();
    }

    @Getter
    @Builder
    public static class PositionInfo {
        private final String assetId;
        private final String ticker;
        private final String name;
        private final Integer currentRiskLevel;  // 1-5 (1: Low, 5: High)
        private final String imageUrl;  // 로고 이미지 URL
        private final Double weightPct;        // 현재 시가총액 기준 비중
        private final Double targetWeightPct;  // 스냅샷 설정 비중 (목표 비중)
        private final Double returnPct;
    }

    /**
     * Baseline 요약 정보
     * 포트폴리오 상세에서 투자 관리 카드 표시용
     */
    @Getter
    @Builder
    @Schema(description = "Baseline 요약 정보")
    public static class BaselineSummary {
        @Schema(description = "원금 (시드 금액)", example = "10000000")
        private final BigDecimal seedMoney;

        @Schema(description = "현재 평가금액", example = "10367000")
        private final BigDecimal totalValue;

        @Schema(description = "잔여 현금", example = "144000")
        private final BigDecimal cashAmount;

        @Schema(description = "수익 금액 (totalValue - seedMoney)", example = "367000")
        private final BigDecimal profitAmount;

        @Schema(description = "수익률 (%)", example = "3.67")
        private final Double profitPct;

        @Schema(description = "통화", example = "KRW")
        private final String baseCurrency;
    }
}