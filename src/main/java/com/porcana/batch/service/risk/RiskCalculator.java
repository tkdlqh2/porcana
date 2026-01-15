package com.porcana.batch.service.risk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 위험도 계산 서비스
 * Volatility, MDD, Worst Day를 기반으로 위험도 점수 및 레벨 계산
 */
@Slf4j
@Service
public class RiskCalculator {

    private static final int VOLATILITY_WINDOW = 60; // 변동성 계산 기간 (60 거래일)
    private static final int MDD_WINDOW = 252; // MDD 계산 기간 (252 거래일, 1년)
    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double SQRT_252 = Math.sqrt(TRADING_DAYS_PER_YEAR);

    // 가중치
    private static final double VOLATILITY_WEIGHT = 0.45;
    private static final double MDD_WEIGHT = 0.45;
    private static final double WORST_DAY_WEIGHT = 0.10;

    /**
     * 가격 데이터로부터 위험도 지표 계산
     *
     * @param prices 시간순 정렬된 가격 리스트 (오래된 것부터)
     * @return RiskMetrics (퍼센타일은 null, 별도로 계산 필요)
     */
    public RiskMetrics calculateMetrics(List<BigDecimal> prices) {
        if (prices == null || prices.size() < 2) {
            log.warn("Insufficient price data for risk calculation: {}", prices != null ? prices.size() : 0);
            return null;
        }

        // 1. 로그 수익률 계산
        List<Double> returns = calculateLogReturns(prices);

        if (returns.isEmpty()) {
            log.warn("No returns calculated from prices");
            return null;
        }

        // 2. Volatility 계산 (최근 60일)
        BigDecimal volatility = calculateVolatility(returns);

        // 3. MDD 계산 (전체 기간)
        BigDecimal maxDrawdown = calculateMaxDrawdown(prices);

        // 4. Worst Day Return 계산
        BigDecimal worstDayReturn = calculateWorstDayReturn(returns);

        return RiskMetrics.builder()
                .volatility(volatility)
                .maxDrawdown(maxDrawdown)
                .worstDayReturn(worstDayReturn)
                .build();
    }

    /**
     * 퍼센타일 기반 위험도 점수 및 레벨 계산
     *
     * @param allMetrics 전체 자산의 RiskMetrics 리스트
     * @return 퍼센타일 및 점수가 포함된 RiskMetrics 리스트
     */
    public List<RiskMetrics> calculateRiskScoresWithPercentiles(List<RiskMetrics> allMetrics) {
        if (allMetrics == null || allMetrics.isEmpty()) {
            return Collections.emptyList();
        }

        // 유효한 메트릭만 필터링
        List<RiskMetrics> validMetrics = allMetrics.stream()
                .filter(m -> m != null && m.getVolatility() != null && m.getMaxDrawdown() != null && m.getWorstDayReturn() != null)
                .toList();

        if (validMetrics.isEmpty()) {
            return Collections.emptyList();
        }

        // 각 지표별 값 추출
        List<Double> volatilities = validMetrics.stream()
                .map(m -> m.getVolatility().doubleValue())
                .collect(Collectors.toList());

        List<Double> mdds = validMetrics.stream()
                .map(m -> m.getMaxDrawdown().doubleValue())
                .collect(Collectors.toList());

        List<Double> worstDays = validMetrics.stream()
                .map(m -> -m.getWorstDayReturn().doubleValue()) // 음수 처리 (더 큰 하락이 더 위험)
                .collect(Collectors.toList());

        // 퍼센타일 계산 및 점수 산출
        List<RiskMetrics> result = new ArrayList<>();
        for (RiskMetrics metrics : validMetrics) {
            double volPct = calculatePercentileRank(metrics.getVolatility().doubleValue(), volatilities);
            double mddPct = calculatePercentileRank(metrics.getMaxDrawdown().doubleValue(), mdds);
            double worstPct = calculatePercentileRank(-metrics.getWorstDayReturn().doubleValue(), worstDays);

            // RiskScore 계산 (0~100)
            double riskScore = 100.0 * (VOLATILITY_WEIGHT * volPct + MDD_WEIGHT * mddPct + WORST_DAY_WEIGHT * worstPct);

            // RiskLevel 매핑 (1~5)
            int riskLevel = mapScoreToLevel(riskScore);

            result.add(RiskMetrics.builder()
                    .volatility(metrics.getVolatility())
                    .maxDrawdown(metrics.getMaxDrawdown())
                    .worstDayReturn(metrics.getWorstDayReturn())
                    .volatilityPercentile(volPct)
                    .mddPercentile(mddPct)
                    .worstDayPercentile(worstPct)
                    .riskScore(BigDecimal.valueOf(riskScore).setScale(2, RoundingMode.HALF_UP))
                    .riskLevel(riskLevel)
                    .build());
        }

        return result;
    }

    /**
     * 로그 수익률 계산
     * r_t = ln(P_t / P_{t-1})
     */
    private List<Double> calculateLogReturns(List<BigDecimal> prices) {
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal prevPrice = prices.get(i - 1);
            BigDecimal currentPrice = prices.get(i);

            if (prevPrice.compareTo(BigDecimal.ZERO) > 0 && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                double logReturn = Math.log(currentPrice.divide(prevPrice, 10, RoundingMode.HALF_UP).doubleValue());
                returns.add(logReturn);
            }
        }
        return returns;
    }

    /**
     * 변동성 계산 (연율화된 표준편차)
     * vol = std(r_{t-59..t}) × √252
     */
    private BigDecimal calculateVolatility(List<Double> returns) {
        // 최근 60일치 또는 전체 데이터 사용
        int startIdx = Math.max(0, returns.size() - VOLATILITY_WINDOW);
        List<Double> recentReturns = returns.subList(startIdx, returns.size());

        if (recentReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 평균 계산
        double mean = recentReturns.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        // 표준편차 계산
        double variance = recentReturns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // 연율화
        double annualizedVol = stdDev * SQRT_252;

        return BigDecimal.valueOf(annualizedVol).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 최대낙폭 (MDD) 계산
     * mdd = max_t(1 - P_t / max(P_{0..t}))
     */
    private BigDecimal calculateMaxDrawdown(List<BigDecimal> prices) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 최근 252일치 또는 전체 데이터 사용
        int startIdx = Math.max(0, prices.size() - MDD_WINDOW);
        List<BigDecimal> recentPrices = prices.subList(startIdx, prices.size());

        BigDecimal maxPrice = recentPrices.get(0);
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (BigDecimal price : recentPrices) {
            // 현재까지의 최고가 업데이트
            if (price.compareTo(maxPrice) > 0) {
                maxPrice = price;
            }

            // 낙폭 계산: (최고가 - 현재가) / 최고가
            if (maxPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = maxPrice.subtract(price)
                        .divide(maxPrice, 10, RoundingMode.HALF_UP);

                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 1일 최악 하락률 계산
     * worst = min(r_{t-251..t})
     */
    private BigDecimal calculateWorstDayReturn(List<Double> returns) {
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 최근 252일치 또는 전체 데이터 사용
        int startIdx = Math.max(0, returns.size() - MDD_WINDOW);
        List<Double> recentReturns = returns.subList(startIdx, returns.size());

        double worstReturn = recentReturns.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);

        return BigDecimal.valueOf(worstReturn).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 퍼센타일 순위 계산 (0~1)
     * 값이 전체 분포에서 어느 위치에 있는지 계산
     */
    private double calculatePercentileRank(double value, List<Double> allValues) {
        if (allValues.isEmpty()) {
            return 0.0;
        }

        long countBelow = allValues.stream()
                .filter(v -> v < value)
                .count();

        return (double) countBelow / allValues.size();
    }

    /**
     * RiskScore를 RiskLevel (1~5)로 매핑
     * 0~20 → 1, 20~40 → 2, 40~60 → 3, 60~80 → 4, 80~100 → 5
     */
    private int mapScoreToLevel(double score) {
        if (score < 20) return 1;
        if (score < 40) return 2;
        if (score < 60) return 3;
        if (score < 80) return 4;
        return 5;
    }
}