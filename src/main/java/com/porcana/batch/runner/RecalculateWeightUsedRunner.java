package com.porcana.batch.runner;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.PortfolioSnapshot;
import com.porcana.domain.portfolio.entity.PortfolioSnapshotAsset;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ApplicationRunner for recalculating weightUsed in SnapshotAssetDailyReturn
 *
 * This runner recalculates the weightUsed field to reflect market-cap based weights
 * instead of fixed snapshot weights.
 *
 * Usage: Set batch.runner.recalculate-weight-used.enabled=true in application.yml
 * Default: false (disabled)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "batch.runner.recalculate-weight-used",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class RecalculateWeightUsedRunner implements ApplicationRunner {

    private final PortfolioRepository portfolioRepository;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;
    private final PortfolioDailyReturnRepository dailyReturnRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;

    /**
     * 초기 가상 투자금 (원화 기준)
     */
    private static final BigDecimal INITIAL_INVESTMENT_KRW = new BigDecimal("10000000.00");

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========================================");
        log.info("Starting WeightUsed Recalculation Runner");
        log.info("========================================");

        // Get all portfolios
        List<Portfolio> portfolios = portfolioRepository.findAll();
        log.info("Found {} portfolios to process", portfolios.size());

        if (portfolios.isEmpty()) {
            log.info("No portfolios to process. Exiting.");
            return;
        }

        int totalProcessed = 0;
        int totalFailed = 0;

        for (int i = 0; i < portfolios.size(); i++) {
            Portfolio portfolio = portfolios.get(i);
            log.info("[{}/{}] Processing portfolio: {} ({})",
                    i + 1, portfolios.size(), portfolio.getName(), portfolio.getId());

            try {
                int processed = recalculatePortfolioWeights(portfolio);
                totalProcessed += processed;
                log.info("  ✓ Recalculated {} daily returns", processed);
            } catch (Exception e) {
                totalFailed++;
                log.error("  ✗ Failed to recalculate portfolio {}: {}",
                        portfolio.getId(), e.getMessage(), e);
            }
        }

        log.info("========================================");
        log.info("WeightUsed Recalculation completed");
        log.info("Total portfolios: {}", portfolios.size());
        log.info("Successfully processed: {}", portfolios.size() - totalFailed);
        log.info("Failed: {}", totalFailed);
        log.info("Total daily returns recalculated: {}", totalProcessed);
        log.info("========================================");
    }

    /**
     * Recalculate market-cap based weights for a single portfolio
     *
     * @param portfolio Portfolio to recalculate
     * @return Number of daily returns recalculated
     */
    @Transactional
    protected int recalculatePortfolioWeights(Portfolio portfolio) {
        UUID portfolioId = portfolio.getId();

        // Get all asset daily returns for this portfolio, ordered by date
        List<SnapshotAssetDailyReturn> allReturns = snapshotAssetDailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (allReturns.isEmpty()) {
            log.debug("No daily returns found for portfolio {}", portfolioId);
            return 0;
        }

        // Get all portfolio daily returns, ordered by date
        List<PortfolioDailyReturn> portfolioDailyReturns = dailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        Map<LocalDate, PortfolioDailyReturn> portfolioReturnMap = portfolioDailyReturns.stream()
                .collect(Collectors.toMap(PortfolioDailyReturn::getReturnDate, pdr -> pdr));

        // Group by return date
        Map<LocalDate, List<SnapshotAssetDailyReturn>> returnsByDate = allReturns.stream()
                .collect(Collectors.groupingBy(SnapshotAssetDailyReturn::getReturnDate));

        int recalculated = 0;

        // Process each date in order
        List<LocalDate> sortedDates = new ArrayList<>(returnsByDate.keySet());
        Collections.sort(sortedDates);

        for (LocalDate returnDate : sortedDates) {
            List<SnapshotAssetDailyReturn> returns = returnsByDate.get(returnDate);
            PortfolioDailyReturn portfolioDailyReturn = portfolioReturnMap.get(returnDate);

            try {
                recalculateWeightsForDate(portfolioId, returnDate, returns, portfolioDailyReturn);
                recalculated += returns.size();
            } catch (Exception e) {
                log.error("Failed to recalculate weights for portfolio {} on {}: {}",
                        portfolioId, returnDate, e.getMessage());
            }
        }

        return recalculated;
    }

    /**
     * Recalculate market-cap based weights for a specific date
     */
    private void recalculateWeightsForDate(UUID portfolioId, LocalDate returnDate,
                                           List<SnapshotAssetDailyReturn> returns,
                                           PortfolioDailyReturn portfolioDailyReturn) {
        if (returns.isEmpty()) {
            return;
        }

        // Get snapshot (should be same for all returns in this date)
        UUID snapshotId = returns.get(0).getSnapshotId();

        // Get initial weights from snapshot
        List<PortfolioSnapshotAsset> snapshotAssets = portfolioSnapshotAssetRepository.findBySnapshotId(snapshotId);
        Map<UUID, BigDecimal> initialWeightMap = snapshotAssets.stream()
                .collect(Collectors.toMap(
                        PortfolioSnapshotAsset::getAssetId,
                        PortfolioSnapshotAsset::getWeight
                ));

        // Calculate current values (KRW-based)
        BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;
        Map<UUID, BigDecimal> currentValueKrwMap = new HashMap<>();

        for (SnapshotAssetDailyReturn dailyReturn : returns) {
            UUID assetId = dailyReturn.getAssetId();
            BigDecimal initialWeight = initialWeightMap.get(assetId);

            if (initialWeight == null) {
                log.warn("No initial weight found for asset {} in snapshot {}", assetId, snapshotId);
                continue;
            }

            // Calculate initial investment amount in KRW
            BigDecimal initialValueKrw = INITIAL_INVESTMENT_KRW
                    .multiply(initialWeight)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // Calculate current value: initialValueKrw × (1 + totalReturn/100)
            // assetReturnTotal is cumulative return from snapshot start date
            BigDecimal returnMultiplier = BigDecimal.ONE.add(
                    dailyReturn.getAssetReturnTotal().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            );
            BigDecimal currentValueKrw = initialValueKrw.multiply(returnMultiplier)
                    .setScale(2, RoundingMode.HALF_UP);

            currentValueKrwMap.put(assetId, currentValueKrw);
            totalCurrentValueKrw = totalCurrentValueKrw.add(currentValueKrw);
        }

        // Update asset returns (weights and values) using reflection
        List<SnapshotAssetDailyReturn> updatedReturns = new ArrayList<>();

        for (SnapshotAssetDailyReturn dailyReturn : returns) {
            UUID assetId = dailyReturn.getAssetId();
            BigDecimal currentValueKrw = currentValueKrwMap.get(assetId);

            if (currentValueKrw == null || totalCurrentValueKrw.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Calculate current weight based on market value (KRW)
            BigDecimal currentWeight = currentValueKrw
                    .divide(totalCurrentValueKrw, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Update weightUsed and valueKrw using reflection
            try {
                java.lang.reflect.Field weightUsedField = SnapshotAssetDailyReturn.class.getDeclaredField("weightUsed");
                weightUsedField.setAccessible(true);
                weightUsedField.set(dailyReturn, currentWeight);

                java.lang.reflect.Field valueKrwField = SnapshotAssetDailyReturn.class.getDeclaredField("valueKrw");
                valueKrwField.setAccessible(true);
                valueKrwField.set(dailyReturn, currentValueKrw);

                updatedReturns.add(dailyReturn);
            } catch (Exception e) {
                log.error("Failed to update fields for asset {}: {}", assetId, e.getMessage());
            }
        }

        // Save updated asset returns
        if (!updatedReturns.isEmpty()) {
            snapshotAssetDailyReturnRepository.saveAll(updatedReturns);
        }

        // Update portfolio daily return's totalValueKrw
        if (portfolioDailyReturn != null) {
            try {
                java.lang.reflect.Field totalValueKrwField = PortfolioDailyReturn.class.getDeclaredField("totalValueKrw");
                totalValueKrwField.setAccessible(true);
                totalValueKrwField.set(portfolioDailyReturn, totalCurrentValueKrw);

                dailyReturnRepository.save(portfolioDailyReturn);
                log.debug("Updated portfolio daily return for {}: totalValueKrw={}", returnDate, totalCurrentValueKrw);
            } catch (Exception e) {
                log.error("Failed to update totalValueKrw for portfolio daily return on {}: {}",
                        returnDate, e.getMessage());
            }
        }
    }
}