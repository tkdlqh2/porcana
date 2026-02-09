package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.us.FmpAssetProvider;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Batch job for fetching US market assets
 *
 * Steps:
 * 1. Fetch S&P 500 constituents from FMP API (already tagged with SP500)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsAssetBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job usAssetJob() {
        return new JobBuilder("usAssetJob", jobRepository)
                .listener(batchNotificationListener)
                .start(fetchUsAssetsStep())
                .next(fetchUsHistoricalPricesStep())
                .build();
    }

    /**
     * Step 1: Fetch S&P 500 constituents from FMP
     * Assets are already tagged with SP500 and marked as active by the provider
     */
    @Bean
    public Step fetchUsAssetsStep() {
        return new StepBuilder("fetchUsAssetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting S&P 500 fetch from FMP");

                    try {
                        List<AssetBatchDto> assets = fmpProvider.fetchAssets();
                        log.info("Fetched {} S&P 500 constituents from FMP", assets.size());

                        int created = 0;
                        int updated = 0;

                        for (AssetBatchDto dto : assets) {
                            boolean exists = assetRepository.existsBySymbolAndMarket(
                                    dto.getSymbol(), dto.getMarket());

                            if (exists) {
                                // Update existing
                                Asset existing = assetRepository.findBySymbolAndMarket(
                                                dto.getSymbol(), dto.getMarket())
                                        .orElseThrow();
                                dto.updateEntity(existing);
                                assetRepository.save(existing);
                                updated++;
                            } else {
                                // Create new
                                assetRepository.save(dto.toEntity());
                                created++;
                            }
                        }

                        log.info("S&P 500 upsert complete: {} created, {} updated",
                                created, updated);

                    } catch (Exception e) {
                        log.error("Failed to fetch S&P 500 constituents", e);
                        throw new RuntimeException("US asset fetch failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 2: Fetch historical prices for recently created assets
     * Fetches prices for assets created within the last 24 hours
     */
    @Bean
    public Step fetchUsHistoricalPricesStep() {
        return new StepBuilder("fetchUsHistoricalPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price fetch for US assets");

                    // Find assets created in the last 24 hours
                    LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
                    List<Asset> recentAssets = assetRepository.findByMarketAndCreatedAtAfter(
                            Asset.Market.US, oneDayAgo);

                    log.info("Found {} US assets created in the last 24 hours", recentAssets.size());

                    int totalPricesFetched = 0;
                    int assetsProcessed = 0;

                    for (Asset asset : recentAssets) {
                        try {
                            // Check if historical prices already exist
                            boolean hasHistoricalData = assetPriceRepository.existsByAsset(asset);
                            if (hasHistoricalData) {
                                log.info("Asset {} already has historical price data, skipping", asset.getSymbol());
                                continue;
                            }

                            log.info("Fetching historical prices for {}", asset.getSymbol());
                            List<AssetPrice> prices = fmpProvider.fetchHistoricalPrices(asset);

                            if (!prices.isEmpty()) {
                                // Save all prices at once
                                assetPriceRepository.saveAll(prices);
                                totalPricesFetched += prices.size();
                                assetsProcessed++;
                                log.info("Saved {} historical prices for {}", prices.size(), asset.getSymbol());
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(200);

                        } catch (Exception e) {
                            log.warn("Failed to fetch historical prices for {}: {}",
                                    asset.getSymbol(), e.getMessage());
                        }
                    }

                    log.info("Historical price fetch complete: {} prices saved for {} assets",
                            totalPricesFetched, assetsProcessed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}