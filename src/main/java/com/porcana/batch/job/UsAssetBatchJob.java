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
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * Weekly job that keeps US stock status up to date via FMP API.
 *
 * Responsibility: "Is each symbol still actively trading?"
 * - Step 1: For every US STOCK in DB, call FMP profile → update active flag and metadata
 *           Chunk-oriented (size 50) so each chunk commits independently — avoids a single
 *           long-running transaction over potentially 1000+ symbols.
 * - Step 2: For active stocks with no price history, backfill historical prices
 * - Step 3: Finish ACTIVE portfolios that contain any inactive asset
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

    private static final int STATUS_CHECK_CHUNK_SIZE = 50;
    private static final int PRICE_BACKFILL_CHUNK_SIZE = 20;

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
    //
    // Chunk-oriented (50 per chunk) so transactions commit incrementally.
    // Each chunk: read 50 stocks → call FMP per stock → saveAll(chunk).
    // Individual FMP failures are skipped (up to 100 total) so one bad symbol
    // does not abort the entire step.
    // -------------------------------------------------------------------------

    @Bean
    public Step checkUsAssetStatusStep() {
        return new StepBuilder("checkUsAssetStatusStep", jobRepository)
                .<Asset, Asset>chunk(STATUS_CHECK_CHUNK_SIZE, transactionManager)
                .reader(usAssetStatusReader())
                .processor(usAssetStatusProcessor())
                .writer(usAssetStatusWriter())
                .faultTolerant()
                .skip(RestClientException.class)
                .noSkip(InterruptedException.class)
                .skipLimit(100)
                .build();
    }

    @Bean
    @StepScope
    public ListItemReader<Asset> usAssetStatusReader() {
        List<Asset> stocks = assetRepository.findByMarketAndType(Asset.Market.US, Asset.AssetType.STOCK);
        log.info("Loaded {} US stocks for status check", stocks.size());
        return new ListItemReader<>(stocks);
    }

    @Bean
    public ItemProcessor<Asset, Asset> usAssetStatusProcessor() {
        return asset -> {
            FmpAssetProvider.ProfileUpdateData profile = fmpProvider.fetchProfileUpdateData(asset.getSymbol());

            if (profile == null) {
                log.warn("No FMP profile for {}. Keeping current status.", asset.getSymbol());
                Thread.sleep(150);
                return asset;
            }

            boolean wasActive = Boolean.TRUE.equals(asset.getActive());
            boolean isNowActive = profile.activelyTrading();

            if (isNowActive && !wasActive) {
                asset.activate();
                log.info("Activated: {}", asset.getSymbol());
            } else if (!isNowActive && wasActive) {
                asset.deactivate();
                log.info("Deactivated: {} (FMP reports no longer actively trading)", asset.getSymbol());
            }

            if (profile.imageUrl() != null && !profile.imageUrl().isBlank()) {
                asset.setImageUrl(profile.imageUrl());
            }
            if (profile.description() != null && !profile.description().isBlank()) {
                asset.setDescription(profile.description());
            }

            Thread.sleep(150);
            return asset;
        };
    }

    @Bean
    public ItemWriter<Asset> usAssetStatusWriter() {
        return chunk -> assetRepository.saveAll(chunk.getItems());
    }

    // -------------------------------------------------------------------------
    // Step 2: Backfill historical prices for active stocks that have none yet
    // -------------------------------------------------------------------------

    @Bean
    public Step fetchHistoricalPricesForActiveStep() {
        return new StepBuilder("fetchHistoricalPricesForActiveStep", jobRepository)
                .<Asset, AssetPriceBackfillResult>chunk(PRICE_BACKFILL_CHUNK_SIZE, transactionManager)
                .reader(activeUsStockReader())
                .processor(assetPriceBackfillProcessor())
                .writer(assetPriceBackfillWriter())
                .build();
    }

    @Bean
    @StepScope
    public ListItemReader<Asset> activeUsStockReader() {
        List<Asset> activeStocks = assetRepository.findByMarketAndTypeAndActiveTrue(
                Asset.Market.US, Asset.AssetType.STOCK);
        log.info("Loaded {} active US stocks for historical price backfill", activeStocks.size());
        return new ListItemReader<>(activeStocks);
    }

    @Bean
    public ItemProcessor<Asset, AssetPriceBackfillResult> assetPriceBackfillProcessor() {
        return asset -> {
            try {
                if (assetPriceRepository.existsByAsset(asset)) {
                    return null;
                }

                log.info("Backfilling historical prices for {}", asset.getSymbol());
                List<AssetPrice> prices = fmpProvider.fetchHistoricalPrices(asset);
                Thread.sleep(200);

                return new AssetPriceBackfillResult(asset.getSymbol(), prices);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Historical price backfill interrupted at {}", asset.getSymbol());
                throw new IllegalStateException("Historical price backfill interrupted", ie);
            } catch (Exception e) {
                log.warn("Failed to backfill prices for {}: {}", asset.getSymbol(), e.getMessage());
                return null;
            }
        };
    }

    @Bean
    public ItemWriter<AssetPriceBackfillResult> assetPriceBackfillWriter() {
        return chunk -> {
            for (AssetPriceBackfillResult backfill : chunk.getItems()) {
                if (backfill == null || backfill.prices().isEmpty()) {
                    continue;
                }
                assetPriceRepository.saveAll(backfill.prices());
                log.info("Saved {} historical price records for {}", backfill.prices().size(), backfill.symbol());
            }
        };
    }

    // -------------------------------------------------------------------------
    // Step 3: Finish ACTIVE portfolios that contain any inactive asset
    //
    // Queries ALL currently inactive US stocks (not just this run's newly
    // deactivated ones) so portfolios missed in previous runs are also caught.
    // -------------------------------------------------------------------------

    @Bean
    public Step finishPortfoliosWithDeactivatedAssetsStep() {
        return new StepBuilder("finishPortfoliosWithDeactivatedAssetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {

                    List<UUID> allInactiveIds = assetRepository
                            .findIdsByMarketAndTypeAndActiveFalse(Asset.Market.US, Asset.AssetType.STOCK);

                    if (allInactiveIds.isEmpty()) {
                        log.info("No inactive US assets found. Portfolio finish step skipped.");
                        return RepeatStatus.FINISHED;
                    }

                    log.info("Checking portfolios against {} inactive US assets", allInactiveIds.size());

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
                        log.info("Finished portfolio {} (contains inactive asset)", portfolio.getId());
                    }

                    portfolioRepository.saveAll(portfoliosToFinish);

                    log.info("Finished {} portfolios due to inactive assets", portfoliosToFinish.size());

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private record AssetPriceBackfillResult(String symbol, List<AssetPrice> prices) {
    }
}
