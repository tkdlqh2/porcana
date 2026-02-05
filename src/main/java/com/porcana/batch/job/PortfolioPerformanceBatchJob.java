package com.porcana.batch.job;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import com.porcana.domain.portfolio.entity.*;
import com.porcana.domain.portfolio.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 포트폴리오 일별 수익률 계산 배치 작업 (Chunk 기반)
 * 모든 ACTIVE 포트폴리오의 일별 수익률을 계산하고 저장합니다.
 * 환율 효과를 분리하여 추적합니다 (return_local vs return_fx)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PortfolioPerformanceBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioSnapshotAssetRepository snapshotAssetRepository;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final PortfolioDailyReturnRepository dailyReturnRepository;
    private final SnapshotAssetDailyReturnRepository assetDailyReturnRepository;

    private static final int CHUNK_SIZE = 10;

    /**
     * 초기 가상 투자금 (원화 기준)
     * 모든 포트폴리오는 10,000,000원으로 시작한다고 가정
     */
    private static final BigDecimal INITIAL_INVESTMENT_KRW = new BigDecimal("10000000.00");

    @Bean
    public Job portfolioPerformanceJob() {
        return new JobBuilder("portfolioPerformanceJob", jobRepository)
                .start(calculatePortfolioPerformanceStep())
                .build();
    }

    @Bean
    public Step calculatePortfolioPerformanceStep() {
        return new StepBuilder("calculatePortfolioPerformanceStep", jobRepository)
                .<Portfolio, PortfolioPerformanceResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(portfolioReader())
                .processor(portfolioPerformanceProcessor(null))
                .writer(portfolioPerformanceWriter())
                .build();
    }

    /**
     * Reader: ACTIVE 포트폴리오를 페이징 방식으로 읽기
     */
    @Bean
    @StepScope
    public RepositoryItemReader<Portfolio> portfolioReader() {
        return new RepositoryItemReaderBuilder<Portfolio>()
                .name("portfolioReader")
                .repository(portfolioRepository)
                .methodName("findByStatus")
                .arguments(Collections.singletonList(PortfolioStatus.ACTIVE))
                .pageSize(CHUNK_SIZE)
                .sorts(Map.of("createdAt", Sort.Direction.ASC))
                .build();
    }

    /**
     * Processor: 포트폴리오별 수익률 계산
     */
    @Bean
    @StepScope
    public ItemProcessor<Portfolio, PortfolioPerformanceResult> portfolioPerformanceProcessor(
            @Value("#{jobParameters['timestamp'] ?: T(System).currentTimeMillis()}") Long timestamp) {

        // If timestamp is null, use current time
        long effectiveTimestamp = (timestamp != null) ? timestamp : System.currentTimeMillis();

        if (timestamp == null) {
            log.info("timestamp parameter is null, using current time: {}", effectiveTimestamp);
        }

        // Convert timestamp to LocalDate (KST timezone) and subtract 1 day
        // (EOD prices are available for the previous day)
        LocalDate targetDate = Instant.ofEpochMilli(effectiveTimestamp)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toLocalDate()
                .minusDays(1);

        log.info("Portfolio Performance Processor initialized with target date: {}", targetDate);

        return portfolio -> {

            // Skip if performance already exists for this date
            if (dailyReturnRepository.existsByPortfolioIdAndReturnDate(portfolio.getId(), targetDate)) {
                log.debug("Performance already exists for portfolio {} on {}, skipping",
                        portfolio.getId(), targetDate);
                return null; // Skip this portfolio
            }

            // Skip if target date is before portfolio start date
            if (targetDate.isBefore(portfolio.getStartedAt())) {
                log.debug("Target date {} is before portfolio {} start date {}, skipping",
                        targetDate, portfolio.getId(), portfolio.getStartedAt());
                return null; // Skip this portfolio
            }

            // Calculate performance
            Optional<PortfolioPerformanceResult> resultOpt = calculatePerformance(portfolio, targetDate);

            if (resultOpt.isEmpty()) {
                log.warn("Failed to calculate performance for portfolio {}: insufficient data", portfolio.getId());
                return null; // Skip this portfolio
            }

            return resultOpt.get();
        };
    }

    /**
     * Writer: 계산된 수익률을 DB에 저장
     */
    @Bean
    @StepScope
    public ItemWriter<PortfolioPerformanceResult> portfolioPerformanceWriter() {
        return chunk -> {
            List<PortfolioDailyReturn> dailyReturns = new ArrayList<>();
            List<SnapshotAssetDailyReturn> assetReturns = new ArrayList<>();

            for (PortfolioPerformanceResult result : chunk) {
                if (result != null) {
                    dailyReturns.add(result.getPortfolioDailyReturn());
                    assetReturns.addAll(result.getAssetDailyReturns());
                }
            }

            // Batch save
            if (!dailyReturns.isEmpty()) {
                dailyReturnRepository.saveAll(dailyReturns);
                log.info("Saved {} portfolio daily returns", dailyReturns.size());
            }

            if (!assetReturns.isEmpty()) {
                assetDailyReturnRepository.saveAll(assetReturns);
                log.info("Saved {} asset daily returns", assetReturns.size());
            }
        };
    }

    /**
     * 포트폴리오의 특정 날짜 수익률을 계산합니다
     *
     * @param portfolio  대상 포트폴리오
     * @param targetDate 계산 날짜
     * @return 계산 결과
     */
    private Optional<PortfolioPerformanceResult> calculatePerformance(Portfolio portfolio, LocalDate targetDate) {
        // Find applicable snapshot (effectiveDate <= targetDate)
        Optional<PortfolioSnapshot> snapshotOpt = snapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        portfolio.getId(), targetDate);

        if (snapshotOpt.isEmpty()) {
            log.warn("No snapshot found for portfolio {} on or before {}", portfolio.getId(), targetDate);
            return Optional.empty();
        }

        PortfolioSnapshot snapshot = snapshotOpt.get();
        LocalDate snapshotDate = snapshot.getEffectiveDate();

        // Get snapshot assets
        List<PortfolioSnapshotAsset> snapshotAssets = snapshotAssetRepository.findBySnapshotId(snapshot.getId());

        if (snapshotAssets.isEmpty()) {
            log.warn("Snapshot {} has no assets", snapshot.getId());
            return Optional.empty();
        }

        // Calculate asset-level returns and contributions
        List<SnapshotAssetDailyReturn> assetReturns = new ArrayList<>();
        BigDecimal totalReturnLocal = BigDecimal.ZERO;
        BigDecimal totalReturnFx = BigDecimal.ZERO;

        // Fetch all assets at once to avoid N+1 query
        List<UUID> assetIds = snapshotAssets.stream()
                .map(PortfolioSnapshotAsset::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(java.util.stream.Collectors.toMap(Asset::getId, asset -> asset));

        // Verify all assets were found
        if (assetMap.size() != assetIds.size()) {
            List<UUID> missingAssetIds = assetIds.stream()
                    .filter(id -> !assetMap.containsKey(id))
                    .toList();
            log.warn("Some assets not found: {}", missingAssetIds);
            return Optional.empty();
        }

        // First pass: Calculate returns and current values (KRW-based) for all assets
        List<AssetCalculation> calculations = new ArrayList<>();
        BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;

        for (PortfolioSnapshotAsset snapshotAsset : snapshotAssets) {
            Asset asset = assetMap.get(snapshotAsset.getAssetId());

            // Calculate asset return
            Optional<AssetReturnResult> resultOpt = calculateAssetReturn(asset, snapshotDate, targetDate);
            if (resultOpt.isEmpty()) {
                log.warn("Failed to calculate return for asset {} ({})", asset.getSymbol(), asset.getId());
                return Optional.empty();
            }

            AssetReturnResult result = resultOpt.get();
            BigDecimal initialWeight = snapshotAsset.getWeight();

            // Calculate initial investment amount in KRW
            // 예: 10% → 10,000,000 × 0.10 = 1,000,000원
            BigDecimal initialValueKrw = INITIAL_INVESTMENT_KRW
                    .multiply(initialWeight)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Calculate current value: initialValueKrw × (1 + totalReturn/100)
            // 예: 1,000,000 × 1.20 = 1,200,000원
            BigDecimal returnMultiplier = BigDecimal.ONE.add(
                    result.assetReturnTotal.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            );
            BigDecimal currentValueKrw = initialValueKrw.multiply(returnMultiplier)
                    .setScale(2, RoundingMode.HALF_UP);

            totalCurrentValueKrw = totalCurrentValueKrw.add(currentValueKrw);

            calculations.add(new AssetCalculation(
                    asset,
                    snapshotAsset,
                    result,
                    initialWeight,
                    currentValueKrw
            ));
        }

        // Second pass: Calculate normalized weights and contributions
        for (AssetCalculation calc : calculations) {
            BigDecimal valueKrw = calc.currentValueKrw;

            // Calculate current weight based on market value (KRW)
            // 예: 1,200,000 / 11,000,000 × 100 = 10.91%
            BigDecimal currentWeight = calc.initialWeight;
            if (totalCurrentValueKrw.compareTo(BigDecimal.ZERO) > 0) {
                currentWeight = valueKrw
                        .divide(totalCurrentValueKrw, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Calculate contribution to portfolio return using initial weight
            BigDecimal contributionTotal = calc.result.assetReturnTotal
                    .multiply(calc.initialWeight)  // Use initial weight for contribution
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            // Build asset daily return record with current weight and value
            SnapshotAssetDailyReturn assetReturn = SnapshotAssetDailyReturn.from(
                    portfolio.getId(),
                    snapshot.getId(),
                    calc.asset.getId(),
                    targetDate,
                    currentWeight,  // Use calculated current weight (시가총액 기반)
                    calc.result.assetReturnLocal,
                    calc.result.assetReturnTotal,
                    calc.result.fxReturn,
                    contributionTotal,
                    valueKrw  // 자산 평가금액 (원화)
            );

            assetReturns.add(assetReturn);

            // Accumulate weighted returns using initial weight
            BigDecimal weightedReturnLocal = calc.result.assetReturnLocal
                    .multiply(calc.initialWeight)
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            BigDecimal weightedReturnFx = calc.result.fxReturn
                    .multiply(calc.initialWeight)
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

            totalReturnLocal = totalReturnLocal.add(weightedReturnLocal);
            totalReturnFx = totalReturnFx.add(weightedReturnFx);

            log.debug("Asset {}: local={}%, fx={}%, total={}%, initialWeight={}%, currentWeight={}%, valueKrw={}, contribution={}%",
                    calc.asset.getSymbol(), calc.result.assetReturnLocal, calc.result.fxReturn,
                    calc.result.assetReturnTotal, calc.initialWeight, currentWeight, valueKrw, contributionTotal);
        }

        // Calculate total portfolio return
        BigDecimal totalReturn = totalReturnLocal.add(totalReturnFx);

        // Build portfolio daily return record
        PortfolioDailyReturn dailyReturn = PortfolioDailyReturn.from(
                portfolio.getId(),
                snapshot.getId(),
                targetDate,
                totalReturn,
                totalReturnLocal,
                totalReturnFx,
                totalCurrentValueKrw  // 포트폴리오 전체 평가금액
        );

        log.info("Calculated performance for portfolio {}: total={}% (local={}%, fx={}%), totalValueKrw={}",
                portfolio.getId(), totalReturn, totalReturnLocal, totalReturnFx, totalCurrentValueKrw);

        return Optional.of(new PortfolioPerformanceResult(dailyReturn, assetReturns));
    }

    /**
     * 자산의 수익률을 계산합니다 (로컬 수익률 + 환율 수익률)
     *
     * @param asset       대상 자산
     * @param startDate   시작 날짜 (스냅샷 effective date)
     * @param targetDate  목표 날짜
     * @return 자산 수익률 결과
     */
    private Optional<AssetReturnResult> calculateAssetReturn(Asset asset, LocalDate startDate, LocalDate targetDate) {
        // Get start price
        Optional<AssetPrice> startPriceOpt = findClosestPrice(asset, startDate);
        if (startPriceOpt.isEmpty()) {
            log.warn("No start price found for asset {} on {}", asset.getSymbol(), startDate);
            return Optional.empty();
        }

        // Get target price
        Optional<AssetPrice> targetPriceOpt = findClosestPrice(asset, targetDate);
        if (targetPriceOpt.isEmpty()) {
            log.warn("No target price found for asset {} on {}", asset.getSymbol(), targetDate);
            return Optional.empty();
        }

        BigDecimal startPrice = startPriceOpt.get().getPrice();
        BigDecimal targetPrice = targetPriceOpt.get().getPrice();

        // Calculate local return (price change in local currency)
        BigDecimal assetReturnLocal = targetPrice.subtract(startPrice)
                .divide(startPrice, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        BigDecimal fxReturn = BigDecimal.ZERO;

        // Calculate FX return for US assets
        if (asset.getMarket() == Asset.Market.US) {
            Optional<BigDecimal> fxReturnOpt = calculateFxReturn(startDate, targetDate);
            if (fxReturnOpt.isEmpty()) {
                log.warn("Failed to calculate FX return for {}", asset.getSymbol());
                return Optional.empty();
            }
            fxReturn = fxReturnOpt.get();
        }

        // Total return = local return + FX return
        BigDecimal assetReturnTotal = assetReturnLocal.add(fxReturn);

        return Optional.of(new AssetReturnResult(assetReturnLocal, fxReturn, assetReturnTotal));
    }

    /**
     * 환율 수익률을 계산합니다 (USD/KRW 변동률)
     *
     * @param startDate  시작 날짜
     * @param targetDate 목표 날짜
     * @return 환율 수익률 (%)
     */
    private Optional<BigDecimal> calculateFxReturn(LocalDate startDate, LocalDate targetDate) {
        // Get USD/KRW exchange rate at start date
        Optional<ExchangeRate> startRateOpt = findClosestExchangeRate(CurrencyCode.USD, startDate);
        if (startRateOpt.isEmpty()) {
            log.warn("No USD exchange rate found for start date {}", startDate);
            return Optional.empty();
        }

        // Get USD/KRW exchange rate at target date
        Optional<ExchangeRate> targetRateOpt = findClosestExchangeRate(CurrencyCode.USD, targetDate);
        if (targetRateOpt.isEmpty()) {
            log.warn("No USD exchange rate found for target date {}", targetDate);
            return Optional.empty();
        }

        BigDecimal startRate = startRateOpt.get().getBaseRate();
        BigDecimal targetRate = targetRateOpt.get().getBaseRate();

        // FX return = (targetRate - startRate) / startRate * 100
        BigDecimal fxReturn = targetRate.subtract(startRate)
                .divide(startRate, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return Optional.of(fxReturn);
    }

    /**
     * 특정 날짜에 가장 가까운 자산 가격을 찾습니다
     * (정확한 날짜가 없으면 7일 이내 가장 최근 가격 사용)
     *
     * @param asset 대상 자산
     * @param date  목표 날짜
     * @return 자산 가격
     */
    private Optional<AssetPrice> findClosestPrice(Asset asset, LocalDate date) {
        // Try exact date first
        Optional<AssetPrice> exactPrice = assetPriceRepository.findByAssetAndPriceDate(asset, date);
        if (exactPrice.isPresent()) {
            return exactPrice;
        }

        // Find closest price before target date (within 7 days lookback)
        LocalDate lookbackStart = date.minusDays(7);
        List<AssetPrice> prices = assetPriceRepository.findByAssetAndPriceDateBetweenOrderByPriceDateAsc(
                asset, lookbackStart, date);

        if (prices.isEmpty()) {
            return Optional.empty();
        }

        // Return the closest price (last in ascending order)
        return Optional.of(prices.get(prices.size() - 1));
    }

    /**
     * 특정 날짜에 가장 가까운 환율을 찾습니다
     * (정확한 날짜가 없으면 7일 이내 가장 최근 환율 사용)
     *
     * @param currencyCode 통화 코드
     * @param date         목표 날짜
     * @return 환율
     */
    private Optional<ExchangeRate> findClosestExchangeRate(CurrencyCode currencyCode, LocalDate date) {
        // Try exact date first
        Optional<ExchangeRate> exactRate = exchangeRateRepository.findByCurrencyCodeAndExchangeDate(currencyCode, date);
        if (exactRate.isPresent()) {
            return exactRate;
        }

        // Find closest rate before target date (within 7 days lookback)
        LocalDate lookbackStart = date.minusDays(7);
        List<ExchangeRate> rates = exchangeRateRepository.findByCurrencyCodeAndExchangeDateBetweenOrderByExchangeDateDesc(
                currencyCode, lookbackStart, date);

        if (rates.isEmpty()) {
            return Optional.empty();
        }

        // Return the closest rate (first in descending order)
        return Optional.of(rates.get(0));
    }

    /**
     * 포트폴리오 수익률 계산 결과 wrapper
     */
    @Getter
    private static class PortfolioPerformanceResult {
        private final PortfolioDailyReturn portfolioDailyReturn;
        private final List<SnapshotAssetDailyReturn> assetDailyReturns;

        public PortfolioPerformanceResult(PortfolioDailyReturn portfolioDailyReturn,
                                          List<SnapshotAssetDailyReturn> assetDailyReturns) {
            this.portfolioDailyReturn = portfolioDailyReturn;
            this.assetDailyReturns = assetDailyReturns;
        }
    }

    /**
     * 자산 수익률 계산 결과
     */
    private record AssetReturnResult(
            BigDecimal assetReturnLocal,
            BigDecimal fxReturn,
            BigDecimal assetReturnTotal
    ) {
    }

    /**
     * 자산별 계산 중간 결과 (금액 기반 비중 계산용)
     */
    private record AssetCalculation(
            Asset asset,
            PortfolioSnapshotAsset snapshotAsset,
            AssetReturnResult result,
            BigDecimal initialWeight,
            BigDecimal currentValueKrw  // 현재 평가금액 (원화)
    ) {
    }
}