package com.porcana.batch.runner;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import com.porcana.domain.portfolio.entity.*;
import com.porcana.domain.portfolio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 포트폴리오 수익률 백필 Runner
 *
 * 모든 ACTIVE 포트폴리오의 startedAt부터 어제까지 누락된 수익률을 계산하고 저장합니다.
 *
 * Usage: Set batch.runner.portfolio-performance-backfill.enabled=true
 * Default: false (disabled)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "batch.runner.portfolio-performance-backfill",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class PortfolioPerformanceBackfillRunner implements ApplicationRunner {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioSnapshotAssetRepository snapshotAssetRepository;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PortfolioDailyReturnRepository dailyReturnRepository;
    private final SnapshotAssetDailyReturnRepository assetDailyReturnRepository;

    private PortfolioPerformanceBackfillRunner self;

    @Autowired
    public void setSelf(@Lazy PortfolioPerformanceBackfillRunner self) {
        this.self = self;
    }

    private static final BigDecimal INITIAL_INVESTMENT_KRW = new BigDecimal("10000000.00");

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("Starting Portfolio Performance Backfill");
        log.info("========================================");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // Get all ACTIVE portfolios (excluding deleted)
        List<Portfolio> portfolios = portfolioRepository.findByStatusAndDeletedAtIsNull(PortfolioStatus.ACTIVE);
        log.info("Found {} active portfolios to backfill", portfolios.size());

        int totalPortfolios = portfolios.size();
        int successPortfolios = 0;
        int failedPortfolios = 0;
        int totalDaysInserted = 0;
        int totalDaysSkipped = 0;

        for (int i = 0; i < portfolios.size(); i++) {
            Portfolio portfolio = portfolios.get(i);
            log.info("[{}/{}] Processing portfolio {} (started: {})",
                    i + 1, totalPortfolios, portfolio.getId(), portfolio.getStartedAt());

            try {
                int[] result = self.backfillPortfolio(portfolio, yesterday);
                totalDaysInserted += result[0];
                totalDaysSkipped += result[1];
                successPortfolios++;
                log.info("  Completed: {} days inserted, {} days skipped",
                        result[0], result[1]);

            } catch (Exception e) {
                log.error("  Failed to backfill portfolio {}: {}", portfolio.getId(), e.getMessage(), e);
                failedPortfolios++;
            }
        }

        log.info("========================================");
        log.info("Portfolio Performance Backfill completed");
        log.info("Portfolios: {} success, {} failed", successPortfolios, failedPortfolios);
        log.info("Days: {} inserted, {} skipped", totalDaysInserted, totalDaysSkipped);
        log.info("========================================");
    }

    /**
     * 단일 포트폴리오의 수익률 백필
     *
     * @return int[2] - [0] = inserted count, [1] = skipped count
     */
    @Transactional
    public int[] backfillPortfolio(Portfolio portfolio, LocalDate endDate) {
        int inserted = 0;
        int skipped = 0;

        LocalDate startDate = portfolio.getStartedAt();
        if (startDate == null) {
            log.warn("Portfolio {} has no startedAt date", portfolio.getId());
            return new int[]{0, 0};
        }

        // 스냅샷별 시작가 캐시 (동일 스냅샷 재사용 시 DB 조회 방지)
        Map<UUID, Map<UUID, BigDecimal>> startPriceCache = new HashMap<>();

        // Iterate through each date from startDate to endDate
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Check if performance already exists
            if (dailyReturnRepository.existsByPortfolioIdAndReturnDate(portfolio.getId(), currentDate)) {
                skipped++;
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Calculate and save performance for this date
            boolean success = calculateAndSavePerformance(portfolio, currentDate, startPriceCache);
            if (success) {
                inserted++;
            }

            currentDate = currentDate.plusDays(1);
        }

        return new int[]{inserted, skipped};
    }

    /**
     * 특정 날짜의 포트폴리오 수익률 계산 및 저장
     * @param startPriceCache 스냅샷별 시작가 캐시 (snapshotId -> assetId -> price)
     */
    private boolean calculateAndSavePerformance(Portfolio portfolio, LocalDate targetDate,
                                                 Map<UUID, Map<UUID, BigDecimal>> startPriceCache) {
        // Find applicable snapshot (effectiveDate <= targetDate)
        Optional<PortfolioSnapshot> snapshotOpt = snapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        portfolio.getId(), targetDate);

        if (snapshotOpt.isEmpty()) {
            log.debug("No snapshot found for portfolio {} on or before {}", portfolio.getId(), targetDate);
            return false;
        }

        PortfolioSnapshot snapshot = snapshotOpt.get();
        LocalDate snapshotDate = snapshot.getEffectiveDate();

        // Get snapshot assets
        List<PortfolioSnapshotAsset> snapshotAssets = snapshotAssetRepository.findBySnapshotId(snapshot.getId());
        if (snapshotAssets.isEmpty()) {
            log.debug("Snapshot {} has no assets", snapshot.getId());
            return false;
        }

        // Fetch all assets at once
        List<UUID> assetIds = snapshotAssets.stream()
                .map(PortfolioSnapshotAsset::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, asset -> asset));

        if (assetMap.size() != assetIds.size()) {
            log.debug("Some assets not found for portfolio {}", portfolio.getId());
            return false;
        }

        // 시작가: 캐시에서 조회하거나 없으면 DB에서 로드 후 캐싱
        Map<UUID, BigDecimal> startPrices = startPriceCache.computeIfAbsent(snapshot.getId(), sid -> {
            LocalDate startLookback = snapshotDate.minusDays(7);
            Map<UUID, List<AssetPrice>> startPricesByAsset = loadPricesForAssets(assetIds, startLookback, snapshotDate);
            Map<UUID, BigDecimal> result = new HashMap<>();
            for (UUID assetId : assetIds) {
                List<AssetPrice> prices = startPricesByAsset.getOrDefault(assetId, Collections.emptyList());
                findClosestPriceFromList(prices, snapshotDate)
                        .ifPresent(ap -> result.put(assetId, ap.getPrice()));
            }
            return result;
        });

        // 타겟가: 매번 조회 (7일 윈도우만)
        LocalDate targetLookback = targetDate.minusDays(7);
        Map<UUID, List<AssetPrice>> targetPricesByAsset = loadPricesForAssets(assetIds, targetLookback, targetDate);

        // Calculate returns
        List<AssetCalculation> calculations = new ArrayList<>();
        BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;

        for (PortfolioSnapshotAsset snapshotAsset : snapshotAssets) {
            Asset asset = assetMap.get(snapshotAsset.getAssetId());

            BigDecimal startPrice = startPrices.get(asset.getId());
            if (startPrice == null) {
                log.debug("No start price for asset {} on {}", asset.getSymbol(), snapshotDate);
                return false;
            }

            List<AssetPrice> targetPrices = targetPricesByAsset.getOrDefault(asset.getId(), Collections.emptyList());
            Optional<AssetPrice> targetPriceOpt = findClosestPriceFromList(targetPrices, targetDate);
            if (targetPriceOpt.isEmpty()) {
                log.debug("No target price for asset {} on {}", asset.getSymbol(), targetDate);
                return false;
            }
            BigDecimal targetPrice = targetPriceOpt.get().getPrice();

            Optional<AssetReturnResult> resultOpt = calculateAssetReturnFromPrices(asset, startPrice, targetPrice, snapshotDate, targetDate);
            if (resultOpt.isEmpty()) {
                log.debug("Failed to calculate return for asset {} on {}", asset.getSymbol(), targetDate);
                return false;
            }

            AssetReturnResult result = resultOpt.get();
            BigDecimal initialWeight = snapshotAsset.getWeight();

            BigDecimal initialValueKrw = INITIAL_INVESTMENT_KRW
                    .multiply(initialWeight)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal returnMultiplier = BigDecimal.ONE.add(
                    result.assetReturnTotal().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            );
            BigDecimal currentValueKrw = initialValueKrw.multiply(returnMultiplier)
                    .setScale(2, RoundingMode.HALF_UP);

            totalCurrentValueKrw = totalCurrentValueKrw.add(currentValueKrw);
            calculations.add(new AssetCalculation(asset, snapshotAsset, result, initialWeight, currentValueKrw));
        }

        // Build and save results
        List<SnapshotAssetDailyReturn> assetReturns = new ArrayList<>();
        BigDecimal totalReturnLocal = BigDecimal.ZERO;
        BigDecimal totalReturnFx = BigDecimal.ZERO;

        for (AssetCalculation calc : calculations) {
            BigDecimal currentWeight = calc.initialWeight();
            if (totalCurrentValueKrw.compareTo(BigDecimal.ZERO) > 0) {
                currentWeight = calc.currentValueKrw()
                        .divide(totalCurrentValueKrw, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            BigDecimal contributionTotal = calc.result().assetReturnTotal()
                    .multiply(calc.initialWeight())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            SnapshotAssetDailyReturn assetReturn = SnapshotAssetDailyReturn.from(
                    portfolio.getId(),
                    snapshot.getId(),
                    calc.asset().getId(),
                    targetDate,
                    currentWeight,
                    calc.result().assetReturnLocal(),
                    calc.result().assetReturnTotal(),
                    calc.result().fxReturn(),
                    contributionTotal,
                    calc.currentValueKrw()
            );
            assetReturns.add(assetReturn);

            BigDecimal weightedReturnLocal = calc.result().assetReturnLocal()
                    .multiply(calc.initialWeight())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            BigDecimal weightedReturnFx = calc.result().fxReturn()
                    .multiply(calc.initialWeight())
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            totalReturnLocal = totalReturnLocal.add(weightedReturnLocal);
            totalReturnFx = totalReturnFx.add(weightedReturnFx);
        }

        BigDecimal totalReturn = totalReturnLocal.add(totalReturnFx);

        PortfolioDailyReturn dailyReturn = PortfolioDailyReturn.from(
                portfolio.getId(),
                snapshot.getId(),
                targetDate,
                totalReturn,
                totalReturnLocal,
                totalReturnFx,
                totalCurrentValueKrw
        );

        // Save
        dailyReturnRepository.save(dailyReturn);
        assetDailyReturnRepository.saveAll(assetReturns);

        return true;
    }

    /**
     * 자산 ID 목록에 대해 지정된 날짜 범위의 가격 데이터를 일괄 조회
     */
    private Map<UUID, List<AssetPrice>> loadPricesForAssets(List<UUID> assetIds, LocalDate startDate, LocalDate endDate) {
        if (assetIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 한 번의 쿼리로 모든 자산의 가격 조회 (N+1 방지)
        List<AssetPrice> allPrices = assetPriceRepository.findPricesByAssetIdsAndDateRange(assetIds, startDate, endDate);

        // assetId별로 그룹핑
        return allPrices.stream()
                .collect(Collectors.groupingBy(ap -> ap.getAsset().getId()));
    }

    /**
     * 이미 조회된 시작가/타겟가로 수익률 계산
     */
    private Optional<AssetReturnResult> calculateAssetReturnFromPrices(Asset asset, BigDecimal startPrice,
                                                                        BigDecimal targetPrice, LocalDate startDate,
                                                                        LocalDate targetDate) {
        // Skip if price is invalid (0 or negative)
        if (startPrice.compareTo(BigDecimal.ZERO) <= 0 || targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid price for asset {} (start={}, target={})",
                    asset.getSymbol(), startPrice, targetPrice);
            return Optional.empty();
        }

        BigDecimal assetReturnLocal = targetPrice.subtract(startPrice)
                .divide(startPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal fxReturn = BigDecimal.ZERO;

        if (asset.getMarket() == Asset.Market.US) {
            Optional<BigDecimal> fxReturnOpt = calculateFxReturn(startDate, targetDate);
            if (fxReturnOpt.isEmpty()) {
                return Optional.empty();
            }
            fxReturn = fxReturnOpt.get();
        }

        BigDecimal assetReturnTotal = assetReturnLocal.add(fxReturn);
        return Optional.of(new AssetReturnResult(assetReturnLocal, fxReturn, assetReturnTotal));
    }

    private Optional<BigDecimal> calculateFxReturn(LocalDate startDate, LocalDate targetDate) {
        Optional<ExchangeRate> startRateOpt = findClosestExchangeRate(CurrencyCode.USD, startDate);
        if (startRateOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<ExchangeRate> targetRateOpt = findClosestExchangeRate(CurrencyCode.USD, targetDate);
        if (targetRateOpt.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal startRate = startRateOpt.get().getBaseRate();
        BigDecimal targetRate = targetRateOpt.get().getBaseRate();

        BigDecimal fxReturn = targetRate.subtract(startRate)
                .divide(startRate, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return Optional.of(fxReturn);
    }

    /**
     * 미리 로드된 가격 목록에서 지정된 날짜에 가장 가까운 가격을 찾음
     * (DB 쿼리 없이 메모리에서 처리)
     */
    private Optional<AssetPrice> findClosestPriceFromList(List<AssetPrice> prices, LocalDate date) {
        if (prices.isEmpty()) {
            return Optional.empty();
        }

        // 정확한 날짜 매칭 먼저 시도
        Optional<AssetPrice> exactMatch = prices.stream()
                .filter(p -> p.getPriceDate().equals(date))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        // date 이전의 가장 가까운 가격 찾기 (최대 7일 lookback)
        LocalDate lookbackStart = date.minusDays(7);
        return prices.stream()
                .filter(p -> !p.getPriceDate().isAfter(date) && !p.getPriceDate().isBefore(lookbackStart))
                .max(Comparator.comparing(AssetPrice::getPriceDate));
    }

    private Optional<ExchangeRate> findClosestExchangeRate(CurrencyCode currencyCode, LocalDate date) {
        Optional<ExchangeRate> exactRate = exchangeRateRepository.findByCurrencyCodeAndExchangeDate(currencyCode, date);
        if (exactRate.isPresent()) {
            return exactRate;
        }

        LocalDate lookbackStart = date.minusDays(7);
        List<ExchangeRate> rates = exchangeRateRepository.findByCurrencyCodeAndExchangeDateBetweenOrderByExchangeDateDesc(
                currencyCode, lookbackStart, date);

        if (rates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(rates.get(0));
    }

    private record AssetReturnResult(
            BigDecimal assetReturnLocal,
            BigDecimal fxReturn,
            BigDecimal assetReturnTotal
    ) {}

    private record AssetCalculation(
            Asset asset,
            PortfolioSnapshotAsset snapshotAsset,
            AssetReturnResult result,
            BigDecimal initialWeight,
            BigDecimal currentValueKrw
    ) {}
}