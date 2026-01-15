package com.porcana.batch.service.risk;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.AssetRiskHistoryRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.asset.entity.AssetRiskHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 자산 위험도 계산 및 저장 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetRiskService {

    private static final DateTimeFormatter WEEK_FORMATTER = DateTimeFormatter.ofPattern("YYYY-'W'ww");
    private static final int MIN_PRICE_DATA_REQUIRED = 60; // 최소 60일 데이터 필요

    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final AssetRiskHistoryRepository assetRiskHistoryRepository;
    private final RiskCalculator riskCalculator;

    /**
     * 모든 활성 자산의 위험도 계산 및 저장
     */
    @Transactional
    public void calculateAndSaveAllAssetRisks() {
        log.info("Starting risk calculation for all active assets");

        // 1. 활성화된 모든 자산 조회
        List<Asset> activeAssets = assetRepository.findByActiveTrue();
        log.info("Found {} active assets", activeAssets.size());

        if (activeAssets.isEmpty()) {
            log.warn("No active assets found for risk calculation");
            return;
        }

        // 2. 각 자산별 가격 데이터 조회 및 위험도 메트릭 계산
        Map<UUID, RiskMetrics> assetMetricsMap = new HashMap<>();
        List<RiskMetrics> allMetrics = new ArrayList<>();

        for (Asset asset : activeAssets) {
            try {
                List<AssetPrice> prices = assetPriceRepository.findByAssetIdOrderByPriceDateAsc(asset.getId());

                if (prices.size() < MIN_PRICE_DATA_REQUIRED) {
                    log.debug("Insufficient price data for asset {}: {} days (minimum {} required)",
                            asset.getSymbol(), prices.size(), MIN_PRICE_DATA_REQUIRED);
                    continue;
                }

                // 가격 리스트 추출
                List<BigDecimal> priceValues = prices.stream()
                        .map(AssetPrice::getPrice)
                        .collect(Collectors.toList());

                // 위험도 메트릭 계산
                RiskMetrics metrics = riskCalculator.calculateMetrics(priceValues);

                if (metrics != null) {
                    assetMetricsMap.put(asset.getId(), metrics);
                    allMetrics.add(metrics);
                }

            } catch (Exception e) {
                log.error("Failed to calculate risk metrics for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }

        if (allMetrics.isEmpty()) {
            log.warn("No valid risk metrics calculated for any asset");
            return;
        }

        log.info("Calculated risk metrics for {} assets", allMetrics.size());

        // 3. 퍼센타일 기반 위험도 점수 및 레벨 계산
        List<RiskMetrics> metricsWithScores = riskCalculator.calculateRiskScoresWithPercentiles(allMetrics);

        if (metricsWithScores.size() != assetMetricsMap.size()) {
            log.error("Mismatch in metrics count: calculated={}, original={}",
                    metricsWithScores.size(), assetMetricsMap.size());
            return;
        }

        // 4. 각 자산의 위험도 업데이트 및 이력 저장
        String currentWeek = getCurrentWeek();
        int savedCount = 0;
        int updatedCount = 0;

        // metricsWithScores와 activeAssets를 동일한 순서로 매칭
        Iterator<Asset> assetIterator = activeAssets.iterator();
        Iterator<RiskMetrics> metricsIterator = metricsWithScores.iterator();

        while (assetIterator.hasNext() && metricsIterator.hasNext()) {
            Asset asset = assetIterator.next();

            // 해당 자산의 메트릭이 있는지 확인
            if (!assetMetricsMap.containsKey(asset.getId())) {
                continue;
            }

            RiskMetrics metrics = metricsIterator.next();

            try {
                // Asset의 currentRiskLevel 업데이트
                asset.updateCurrentRiskLevel(metrics.getRiskLevel());
                assetRepository.save(asset);
                updatedCount++;

                // AssetRiskHistory 저장 (중복 체크)
                if (!assetRiskHistoryRepository.existsByAssetIdAndWeek(asset.getId(), currentWeek)) {
                    AssetRiskHistory history = AssetRiskHistory.builder()
                            .asset(asset)
                            .week(currentWeek)
                            .riskLevel(metrics.getRiskLevel())
                            .riskScore(metrics.getRiskScore())
                            .volatility(metrics.getVolatility())
                            .maxDrawdown(metrics.getMaxDrawdown())
                            .worstDayReturn(metrics.getWorstDayReturn())
                            .build();

                    assetRiskHistoryRepository.save(history);
                    savedCount++;
                }

            } catch (Exception e) {
                log.error("Failed to save risk data for asset {}: {}", asset.getSymbol(), e.getMessage(), e);
            }
        }

        log.info("Risk calculation completed. Updated {} assets, Saved {} history records",
                updatedCount, savedCount);
    }

    /**
     * 현재 주차 문자열 생성 (YYYY-WW 포맷)
     */
    private String getCurrentWeek() {
        LocalDate now = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        int weekOfYear = now.get(weekFields.weekOfWeekBasedYear());
        int year = now.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, weekOfYear);
    }
}