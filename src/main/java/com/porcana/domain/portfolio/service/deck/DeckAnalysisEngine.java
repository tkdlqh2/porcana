package com.porcana.domain.portfolio.service.deck;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.personality.DividendProfile;
import com.porcana.domain.asset.entity.personality.Role;
import com.porcana.domain.portfolio.dto.deck.DeckAnalysis;
import com.porcana.domain.portfolio.dto.deck.DeckMetrics;
import com.porcana.domain.portfolio.dto.deck.PositionWithAsset;
import com.porcana.domain.portfolio.entity.deck.DeckSignal;
import com.porcana.domain.portfolio.entity.deck.DeckStyle;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 덱 분석 엔진
 * 포트폴리오 포지션 목록으로부터 분석 결과 계산
 */
@UtilityClass
public class DeckAnalysisEngine {

    /**
     * 포트폴리오 분석 수행
     *
     * @throws IllegalStateException 포지션 비중이 음수인 경우
     */
    public static DeckAnalysis analyze(List<PositionWithAsset> positions) {
        if (positions == null || positions.isEmpty()) {
            return emptyAnalysis();
        }

        // 포지션 유효성 검증
        validatePositions(positions);

        // 지표 계산
        DeckMetrics metrics = calculateMetrics(positions);

        // 스타일 판별
        DeckStyle style = determineStyle(metrics);

        // 시그널 감지
        List<DeckSignal> signals = detectSignals(metrics);

        // 텍스트 해설 생성
        String summary = DeckTextTemplate.generateSummary(style, metrics, signals);
        List<String> strengths = DeckTextTemplate.generateStrengths(style, metrics, signals);
        List<String> weaknesses = DeckTextTemplate.generateWeaknesses(style, metrics, signals);
        List<String> tips = DeckTextTemplate.generateTips(style, metrics, signals);
        String investorFit = DeckTextTemplate.generateInvestorFit(style, metrics);

        return DeckAnalysis.builder()
                .metrics(metrics)
                .style(style)
                .signals(signals)
                .summary(summary)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .tips(tips)
                .investorFit(investorFit)
                .build();
    }

    /**
     * 포지션 유효성 검증
     */
    private static void validatePositions(List<PositionWithAsset> positions) {
        for (PositionWithAsset position : positions) {
            if (position.getWeightPct() < 0) {
                throw new IllegalStateException(
                        "Position weight cannot be negative: " + position.getAsset().getSymbol()
                                + " = " + position.getWeightPct() + "%");
            }
            if (position.getPersonality() == null) {
                throw new IllegalStateException(
                        "Position personality must be computed before analysis: " + position.getAsset().getSymbol());
            }
        }
    }

    /**
     * 지표 계산
     */
    private static DeckMetrics calculateMetrics(List<PositionWithAsset> positions) {
        double totalWeight = positions.stream()
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        if (totalWeight <= 0) {
            throw new IllegalStateException("Total portfolio weight must be positive: " + totalWeight);
        }

        // 위험도: null 제외 자산 비중만 분모로 사용
        double riskWeightSum = positions.stream()
                .filter(p -> p.getAsset().getCurrentRiskLevel() != null)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        double weightedRisk = riskWeightSum > 0
                ? positions.stream()
                    .filter(p -> p.getAsset().getCurrentRiskLevel() != null)
                    .mapToDouble(p -> p.getWeightPct() * p.getAsset().getCurrentRiskLevel())
                    .sum() / riskWeightSum
                : 0.0;

        // 섹터별 비중
        Map<Sector, Double> sectorWeights = positions.stream()
                .filter(p -> p.getAsset().getSector() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getAsset().getSector(),
                        Collectors.summingDouble(PositionWithAsset::getWeightPct)
                ));

        Sector topSector = sectorWeights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        double topSectorWeight = topSector == null ? 0.0 : sectorWeights.get(topSector);

        // Role별 비중 계산
        double growthWeight = calculateRoleWeight(positions, Role.GROWTH);
        double incomeWeight = calculateRoleWeight(positions, Role.INCOME);
        double defensiveWeight = calculateRoleWeight(positions, Role.DEFENSIVE);
        double hedgeWeight = calculateRoleWeight(positions, Role.HEDGE);
        double coreWeight = calculateRoleWeight(positions, Role.CORE);

        // 타입별 비중 (직접 합산)
        double etfWeight = positions.stream()
                .filter(p -> p.getAsset().getType() == Asset.AssetType.ETF)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        double stockWeight = positions.stream()
                .filter(p -> p.getAsset().getType() == Asset.AssetType.STOCK)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        // 시장별 비중 (직접 합산)
        double usWeight = positions.stream()
                .filter(p -> p.getAsset().getMarket() == Asset.Market.US)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        double krWeight = positions.stream()
                .filter(p -> p.getAsset().getMarket() == Asset.Market.KR)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();

        // 배당 관련 비중 계산
        double hasDividendWeight = calculateDividendProfileWeight(positions, DividendProfile.HAS_DIVIDEND)
                + calculateDividendProfileWeight(positions, DividendProfile.DIVIDEND_FOCUSED)
                + calculateDividendProfileWeight(positions, DividendProfile.INCOME_CORE);

        double dividendFocusedWeight = calculateDividendProfileWeight(positions, DividendProfile.DIVIDEND_FOCUSED);

        double incomeCoreWeight = calculateDividendProfileWeight(positions, DividendProfile.INCOME_CORE);

        // 집중도 (상위 3종목)
        List<Double> sortedWeights = positions.stream()
                .map(PositionWithAsset::getWeightPct)
                .sorted(Comparator.reverseOrder())
                .toList();

        double top3Concentration = sortedWeights.stream()
                .limit(3)
                .mapToDouble(Double::doubleValue)
                .sum();

        return DeckMetrics.builder()
                .weightedAverageRisk(round1(weightedRisk))
                .topSector(topSector)
                .topSectorWeight(round1(topSectorWeight))
                .growthWeight(round1(growthWeight))
                .incomeWeight(round1(incomeWeight))
                .defensiveWeight(round1(defensiveWeight))
                .hedgeWeight(round1(hedgeWeight))
                .coreWeight(round1(coreWeight))
                .etfWeight(round1(etfWeight))
                .stockWeight(round1(stockWeight))
                .usWeight(round1(usWeight))
                .krWeight(round1(krWeight))
                .hasDividendWeight(round1(hasDividendWeight))
                .dividendFocusedWeight(round1(dividendFocusedWeight))
                .incomeCoreWeight(round1(incomeCoreWeight))
                .top3Concentration(round1(top3Concentration))
                .assetCount(positions.size())
                .build();
    }

    /**
     * 특정 Role의 비중 계산
     */
    private static double calculateRoleWeight(List<PositionWithAsset> positions, Role role) {
        return positions.stream()
                .filter(p -> p.getPersonality() != null && p.getPersonality().getRole() == role)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();
    }

    /**
     * 특정 DividendProfile의 비중 계산
     */
    private static double calculateDividendProfileWeight(List<PositionWithAsset> positions, DividendProfile profile) {
        return positions.stream()
                .filter(p -> p.getPersonality() != null && p.getPersonality().getDividendProfile() == profile)
                .mapToDouble(PositionWithAsset::getWeightPct)
                .sum();
    }

    /**
     * 소수점 1자리 반올림
     */
    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /**
     * 덱 스타일 판별
     */
    private static DeckStyle determineStyle(DeckMetrics metrics) {
        // 테마 집중이 아주 강하면 먼저 반영
        if (metrics.getTopSectorWeight() >= 55 && metrics.getGrowthWeight() >= 40) {
            return DeckStyle.THEMATIC;
        }

        // 공격형: 평균 위험도 4 이상 + 성장 비중 60% 이상
        if (metrics.getWeightedAverageRisk() >= 4.0 && metrics.getGrowthWeight() >= 60) {
            return DeckStyle.AGGRESSIVE;
        }

        // 현금흐름형: 인컴 비중 40% 이상 또는 인컴 코어 비중 30% 이상
        if (metrics.getIncomeWeight() >= 40 || metrics.getIncomeCoreWeight() >= 30) {
            return DeckStyle.CASHFLOW;
        }

        // 방어형: 방어 + 헤지 비중 40% 이상
        if (metrics.getDefensiveWeight() + metrics.getHedgeWeight() >= 40) {
            return DeckStyle.DEFENSIVE;
        }

        // 성장형: 성장 비중 50% 이상
        if (metrics.getGrowthWeight() >= 50) {
            return DeckStyle.GROWTH;
        }

        // 테마형: 최다 섹터 비중 50% 이상
        if (metrics.getTopSectorWeight() >= 50) {
            return DeckStyle.THEMATIC;
        }

        // 기본: 균형형
        return DeckStyle.BALANCED;
    }

    /**
     * 시그널 감지
     */
    private static List<DeckSignal> detectSignals(DeckMetrics metrics) {
        List<DeckSignal> signals = new ArrayList<>();

        // 섹터 집중 (40% 이상)
        if (metrics.getTopSectorWeight() >= 40) {
            signals.add(DeckSignal.SECTOR_CONCENTRATION);
        }

        // 고위험 노출 (성장 60% 이상 + 평균 위험 4 이상)
        if (metrics.getGrowthWeight() >= 60 && metrics.getWeightedAverageRisk() >= 4.0) {
            signals.add(DeckSignal.HIGH_RISK_EXPOSURE);
        }

        // 방어력 부족 (방어 + 헤지 10% 이하)
        if (metrics.getDefensiveWeight() + metrics.getHedgeWeight() <= 10) {
            signals.add(DeckSignal.LOW_DEFENSE);
        }

        // 종목 집중 (상위 3종목 55% 이상)
        if (metrics.getTop3Concentration() >= 55) {
            signals.add(DeckSignal.HIGH_CONCENTRATION);
        }

        // 인컴 중심 (인컴 비중 40% 이상 또는 인컴 코어 30% 이상)
        if (metrics.getIncomeWeight() >= 40 || metrics.getIncomeCoreWeight() >= 30) {
            signals.add(DeckSignal.HIGH_INCOME);
        }

        // 시장 집중 (90% 이상)
        if (metrics.getUsWeight() >= 90 || metrics.getKrWeight() >= 90) {
            signals.add(DeckSignal.MARKET_CONCENTRATION);
        }
        // 시장 불균형 (80% 이상, 90% 미만)
        else if (metrics.getUsWeight() >= 80 || metrics.getKrWeight() >= 80) {
            signals.add(DeckSignal.MARKET_IMBALANCE);
        }

        // 분산 부족 (5종목 미만 + 상위 3종목 60% 이상)
        if (metrics.getAssetCount() < 5 && metrics.getTop3Concentration() >= 60) {
            signals.add(DeckSignal.LOW_DIVERSIFICATION);
        }

        // 배당 성향 (배당 중심 종목 비중 25% 이상)
        if (metrics.getDividendFocusedWeight() >= 25) {
            signals.add(DeckSignal.DIVIDEND_TILT);
        }

        return signals;
    }

    /**
     * 빈 분석 결과
     */
    private static DeckAnalysis emptyAnalysis() {
        return DeckAnalysis.builder()
                .metrics(DeckMetrics.builder()
                        .weightedAverageRisk(0.0)
                        .topSectorWeight(0.0)
                        .growthWeight(0.0)
                        .incomeWeight(0.0)
                        .defensiveWeight(0.0)
                        .hedgeWeight(0.0)
                        .coreWeight(0.0)
                        .etfWeight(0.0)
                        .stockWeight(0.0)
                        .usWeight(0.0)
                        .krWeight(0.0)
                        .hasDividendWeight(0.0)
                        .dividendFocusedWeight(0.0)
                        .incomeCoreWeight(0.0)
                        .top3Concentration(0.0)
                        .assetCount(0)
                        .build())
                .style(DeckStyle.BALANCED)
                .signals(List.of())
                .summary("포트폴리오에 자산이 없습니다")
                .strengths(List.of())
                .weaknesses(List.of())
                .tips(List.of("자산을 추가하여 포트폴리오를 구성해보세요"))
                .investorFit("")
                .build();
    }
}
