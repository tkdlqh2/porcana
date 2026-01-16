package com.porcana.batch.service.risk;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 위험도 계산 지표
 */
@Getter
@Builder
public class RiskMetrics {
    /**
     * 변동성 (Volatility) - 연율화된 표준편차
     */
    private BigDecimal volatility;

    /**
     * 최대낙폭 (Max Drawdown)
     */
    private BigDecimal maxDrawdown;

    /**
     * 1일 최악 하락률 (Worst Day Return)
     */
    private BigDecimal worstDayReturn;

    /**
     * 위험도 점수 (0~100)
     */
    private BigDecimal riskScore;

    /**
     * 위험도 레벨 (1~5)
     */
    private Integer riskLevel;

    /**
     * 변동성 퍼센타일 (0~1)
     */
    private Double volatilityPercentile;

    /**
     * MDD 퍼센타일 (0~1)
     */
    private Double mddPercentile;

    /**
     * Worst Day 퍼센타일 (0~1)
     */
    private Double worstDayPercentile;
}