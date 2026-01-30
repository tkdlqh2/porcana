package com.porcana.batch.runner;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioSnapshot;
import com.porcana.domain.portfolio.entity.PortfolioSnapshotAsset;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
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
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;

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

        // Group by return date
        Map<LocalDate, List<SnapshotAssetDailyReturn>> returnsByDate = allReturns.stream()
                .collect(Collectors.groupingBy(SnapshotAssetDailyReturn::getReturnDate));

        int recalculated = 0;

        // Process each date in order
        List<LocalDate> sortedDates = new ArrayList<>(returnsByDate.keySet());
        Collections.sort(sortedDates);

        for (LocalDate returnDate : sortedDates) {
            List<SnapshotAssetDailyReturn> returns = returnsByDate.get(returnDate);

            try {
                recalculateWeightsForDate(portfolioId, returnDate, returns);
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
                                           List<SnapshotAssetDailyReturn> returns) {
        if (returns.isEmpty()) {
            return;
        }

        // Get snapshot (should be same for all returns in this date)
        UUID snapshotId = returns.get(0).getSnapshotId();

        // Get snapshot to find effective date
        PortfolioSnapshot snapshot = portfolioSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + snapshotId));

        // Get initial weights from snapshot
        List<PortfolioSnapshotAsset> snapshotAssets = portfolioSnapshotAssetRepository.findBySnapshotId(snapshotId);
        Map<UUID, BigDecimal> initialWeightMap = snapshotAssets.stream()
                .collect(Collectors.toMap(
                        PortfolioSnapshotAsset::getAssetId,
                        PortfolioSnapshotAsset::getWeight
                ));

        // Calculate current values
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        Map<UUID, BigDecimal> currentValueMap = new HashMap<>();

        for (SnapshotAssetDailyReturn dailyReturn : returns) {
            UUID assetId = dailyReturn.getAssetId();
            BigDecimal initialWeight = initialWeightMap.get(assetId);

            if (initialWeight == null) {
                log.warn("No initial weight found for asset {} in snapshot {}", assetId, snapshotId);
                continue;
            }

            // Calculate current value: initialWeight × (1 + totalReturn/100)
            BigDecimal returnMultiplier = BigDecimal.ONE.add(
                    dailyReturn.getAssetReturnTotal().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
            );
            BigDecimal currentValue = initialWeight.multiply(returnMultiplier);
            currentValueMap.put(assetId, currentValue);
            totalCurrentValue = totalCurrentValue.add(currentValue);
        }

        // Update weights using reflection
        List<SnapshotAssetDailyReturn> updatedReturns = new ArrayList<>();

        for (SnapshotAssetDailyReturn dailyReturn : returns) {
            UUID assetId = dailyReturn.getAssetId();
            BigDecimal currentValue = currentValueMap.get(assetId);

            if (currentValue == null || totalCurrentValue.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // Calculate current weight based on market value
            BigDecimal currentWeight = currentValue
                    .divide(totalCurrentValue, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Update weightUsed using reflection
            try {
                java.lang.reflect.Field weightUsedField = SnapshotAssetDailyReturn.class.getDeclaredField("weightUsed");
                weightUsedField.setAccessible(true);
                weightUsedField.set(dailyReturn, currentWeight);
                updatedReturns.add(dailyReturn);
            } catch (Exception e) {
                log.error("Failed to update weightUsed for asset {}: {}", assetId, e.getMessage());
            }
        }

        // Save updated returns
        if (!updatedReturns.isEmpty()) {
            snapshotAssetDailyReturnRepository.saveAll(updatedReturns);
        }
    }
}