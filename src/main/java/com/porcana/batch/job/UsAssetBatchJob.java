package com.porcana.batch.job;

import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.us.FmpAssetProvider;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Weekly job that keeps US stock status up to date via FMP API.
 *
 * Responsibility: "Is each symbol still actively trading?"
 * - Step 1: For every US STOCK in DB, call FMP profile → update active flag and metadata
 * - Step 2: For active stocks with no price history, backfill historical prices (covers newly activated symbols)
 * - Step 3: Finish ACTIVE portfolios that contain newly-deactivated assets
 *
 * Symbol discovery (which stocks to add) is handled by UsUniverseSyncJob (monthly).
 * ETF status is handled by UsEtfBatchJob (separate job, CSV-driven).
 *
 * Schedule: Every Sunday 02:00 KST
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsAssetBatchJob {

    private static final String DEACTIVATED_IDS_KEY = "deactivatedAssetIds";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioRepository portfolioRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job usAssetJob() {
        return new JobBuilder("usAssetJob", jobRepository)
                .listener(batchNotificationListener)
                .start(checkUsAssetStatusStep())
                .next(fetchHistoricalPricesForActiveStep())
                .next(finishPortfoliosWithDeactivatedAssetsStep())
                .build();
    }

    // -------------------------------------------------------------------------
    // Step 1: Check active status for every US stock via FMP profile API
    // -------------------------------------------------------------------------

    @Bean
    public Step checkUsAssetStatusStep() {
        return new StepBuilder("checkUsAssetStatusStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting US asset status check via FMP");

                    List<Asset> stocks = assetRepository.findByMarketAndType(Asset.Market.US, Asset.AssetType.STOCK);
                    log.info("Found {} US stocks to check", stocks.size());

                    List<UUID> deactivatedIds = new ArrayList<>();
                    List<Asset> changedAssets = new ArrayList<>();
                    int activated = 0;
                    int deactivated = 0;
                    int unchanged = 0;
                    int failed = 0;

                    for (Asset asset : stocks) {
                        try {
                            FmpAssetProvider.ProfileUpdateData profile =
                                    fmpProvider.fetchProfileUpdateData(asset.getSymbol());

                            if (profile == null) {
                                log.warn("No FMP profile found for {}. Keeping current status.", asset.getSymbol());
                                failed++;
                                Thread.sleep(150);
                                continue;
                            }

                            boolean wasActive = Boolean.TRUE.equals(asset.getActive());
                            boolean isNowActive = profile.activelyTrading();

                            if (isNowActive && !wasActive) {
                                asset.activate();
                                activated++;
                            } else if (!isNowActive && wasActive) {
                                asset.deactivate();
                                deactivatedIds.add(asset.getId());
                                deactivated++;
                                log.info("Deactivated: {} (FMP reports no longer actively trading)", asset.getSymbol());
                            } else {
                                unchanged++;
                            }

                            // Update profile metadata
                            if (profile.imageUrl() != null && !profile.imageUrl().isBlank()) {
                                asset.setImageUrl(profile.imageUrl());
                            }
                            if (profile.description() != null && !profile.description().isBlank()) {
                                asset.setDescription(profile.description());
                            }

                            changedAssets.add(asset);
                            Thread.sleep(150);

                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Status check interrupted at symbol: {}", asset.getSymbol());
                            break;
                        } catch (Exception e) {
                            log.warn("Failed to check status for {}: {}", asset.getSymbol(), e.getMessage());
                            failed++;
                        }
                    }

                    assetRepository.saveAll(changedAssets);

                    // Share deactivated IDs with step 3 via job execution context (stored as String for safe serialization)
                    List<String> deactivatedIdStrings = deactivatedIds.stream()
                            .map(UUID::toString)
                            .toList();
                    chunkContext.getStepContext().getStepExecution()
                            .getJobExecution().getExecutionContext()
                            .put(DEACTIVATED_IDS_KEY, new ArrayList<>(deactivatedIdStrings));

                    log.info("US asset status check complete: {} activated, {} deactivated, {} unchanged, {} failed",
                            activated, deactivated, unchanged, failed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // -------------------------------------------------------------------------
    // Step 2: Backfill historical prices for active stocks that have none yet
    // -------------------------------------------------------------------------

    @Bean
    public Step fetchHistoricalPricesForActiveStep() {
        return new StepBuilder("fetchHistoricalPricesForActiveStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price backfill for US stocks without price data");

                    List<Asset> activeStocks = assetRepository.findByMarketAndTypeAndActiveTrue(
                            Asset.Market.US, Asset.AssetType.STOCK);

                    int backfilled = 0;
                    int skipped = 0;

                    for (Asset asset : activeStocks) {
                        try {
                            if (assetPriceRepository.existsByAsset(asset)) {
                                skipped++;
                                continue;
                            }

                            log.info("Backfilling historical prices for {}", asset.getSymbol());
                            List<AssetPrice> prices = fmpProvider.fetchHistoricalPrices(asset);

                            if (!prices.isEmpty()) {
                                assetPriceRepository.saveAll(prices);
                                backfilled++;
                                log.info("Saved {} historical price records for {}", prices.size(), asset.getSymbol());
                            }

                            Thread.sleep(200);

                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Historical price backfill interrupted at {}", asset.getSymbol());
                            break;
                        } catch (Exception e) {
                            log.warn("Failed to backfill prices for {}: {}", asset.getSymbol(), e.getMessage());
                        }
                    }

                    log.info("Historical price backfill complete: {} assets backfilled, {} already had data",
                            backfilled, skipped);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // -------------------------------------------------------------------------
    // Step 3: Finish ACTIVE portfolios that contain any inactive asset
    //
    // Uses ALL currently inactive US stocks (not just newly deactivated ones),
    // so portfolios that slipped through in previous runs are also caught.
    // -------------------------------------------------------------------------

    @Bean
    public Step finishPortfoliosWithDeactivatedAssetsStep() {
        return new StepBuilder("finishPortfoliosWithDeactivatedAssetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {

                    @SuppressWarnings("unchecked")
                    List<String> newlyDeactivatedStrings = (List<String>) chunkContext.getStepContext()
                            .getStepExecution().getJobExecution().getExecutionContext()
                            .get(DEACTIVATED_IDS_KEY);

                    int newlyDeactivatedCount = newlyDeactivatedStrings == null ? 0 : newlyDeactivatedStrings.size();

                    // Use all currently inactive US stocks, not just this run's newly deactivated ones.
                    // This ensures portfolios affected by previous-run deactivations are also caught.
                    List<UUID> allInactiveIds = assetRepository
                            .findByMarketAndType(Asset.Market.US, Asset.AssetType.STOCK)
                            .stream()
                            .filter(a -> !Boolean.TRUE.equals(a.getActive()))
                            .map(Asset::getId)
                            .toList();

                    if (allInactiveIds.isEmpty()) {
                        log.info("No inactive US assets found. Portfolio finish step skipped.");
                        return RepeatStatus.FINISHED;
                    }

                    log.info("Checking portfolios against {} inactive assets ({} newly deactivated this run)",
                            allInactiveIds.size(), newlyDeactivatedCount);

                    List<UUID> affectedPortfolioIds =
                            portfolioAssetRepository.findPortfolioIdsByAssetIdIn(allInactiveIds);

                    if (affectedPortfolioIds.isEmpty()) {
                        log.info("No active portfolios affected by inactive assets.");
                        return RepeatStatus.FINISHED;
                    }

                    List<Portfolio> portfoliosToFinish =
                            portfolioRepository.findActiveByIdIn(affectedPortfolioIds, PortfolioStatus.ACTIVE);

                    for (Portfolio portfolio : portfoliosToFinish) {
                        portfolio.finish();
                        portfolioRepository.save(portfolio);
                        log.info("Finished portfolio {} (contains inactive asset)", portfolio.getId());
                    }

                    log.info("Finished {} portfolios due to inactive assets", portfoliosToFinish.size());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}