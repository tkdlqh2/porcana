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
            boolean success = calculateAndSavePerformance(portfolio, currentDate);
            if (success) {
                inserted++;
            }

            currentDate = currentDate.plusDays(1);
        }

        return new int[]{inserted, skipped};
    }

    /**
     * 특정 날짜의 포트폴리오 수익률 계산 및 저장
     */
    private boolean calculateAndSavePerformance(Portfolio portfolio, LocalDate targetDate) {
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

        // Calculate returns
        List<AssetCalculation> calculations = new ArrayList<>();
        BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;

        for (PortfolioSnapshotAsset snapshotAsset : snapshotAssets) {
            Asset asset = assetMap.get(snapshotAsset.getAssetId());

            Optional<AssetReturnResult> resultOpt = calculateAssetReturn(asset, snapshotDate, targetDate);
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

    private Optional<AssetReturnResult> calculateAssetReturn(Asset asset, LocalDate startDate, LocalDate targetDate) {
        Optional<AssetPrice> startPriceOpt = findClosestPrice(asset, startDate);
        if (startPriceOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<AssetPrice> targetPriceOpt = findClosestPrice(asset, targetDate);
        if (targetPriceOpt.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal startPrice = startPriceOpt.get().getPrice();
        BigDecimal targetPrice = targetPriceOpt.get().getPrice();

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

    private Optional<AssetPrice> findClosestPrice(Asset asset, LocalDate date) {
        Optional<AssetPrice> exactPrice = assetPriceRepository.findByAssetAndPriceDate(asset, date);
        if (exactPrice.isPresent()) {
            return exactPrice;
        }

        LocalDate lookbackStart = date.minusDays(7);
        List<AssetPrice> prices = assetPriceRepository.findByAssetAndPriceDateBetweenOrderByPriceDateAsc(
                asset, lookbackStart, date);

        if (prices.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(prices.get(prices.size() - 1));
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