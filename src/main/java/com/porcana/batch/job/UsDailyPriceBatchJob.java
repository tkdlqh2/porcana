package com.porcana.batch.job;

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

import java.util.List;

/**
 * Daily price update batch job for US market
 * Fetches and updates latest EOD prices for all active US assets
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsDailyPriceBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;

    @Bean
    public Job usDailyPriceJob() {
        return new JobBuilder("usDailyPriceJob", jobRepository)
                .start(updateUsDailyPricesStep())
                .build();
    }

    /**
     * Update daily prices for all active US assets
     */
    @Bean
    public Step updateUsDailyPricesStep() {
        return new StepBuilder("updateUsDailyPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting daily price update for US market");

                    // Find all active US assets
                    List<Asset> activeAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.US);
                    log.info("Found {} active US assets", activeAssets.size());

                    int totalProcessed = 0;
                    int totalSaved = 0;
                    int totalSkipped = 0;
                    int totalFailed = 0;

                    for (Asset asset : activeAssets) {
                        try {
                            totalProcessed++;

                            // Fetch latest price
                            AssetPrice latestPrice = fmpProvider.fetchDailyPrice(asset);

                            if (latestPrice == null) {
                                log.warn("No daily price data for symbol: {}", asset.getSymbol());
                                totalFailed++;
                                continue;
                            }

                            // Check if price already exists for this date
                            boolean exists = assetPriceRepository.existsByAssetAndPriceDate(
                                    asset, latestPrice.getPriceDate());

                            if (exists) {
                                log.debug("Price already exists for {} on {}, skipping",
                                        asset.getSymbol(), latestPrice.getPriceDate());
                                totalSkipped++;
                            } else {
                                // Save new price
                                assetPriceRepository.save(latestPrice);
                                log.info("Saved price for {} on {}: ${}",
                                        asset.getSymbol(), latestPrice.getPriceDate(), latestPrice.getPrice());
                                totalSaved++;
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(150);

                        } catch (Exception e) {
                            log.error("Failed to update price for symbol: {}", asset.getSymbol(), e);
                            totalFailed++;
                        }

                        // Log progress every 50 assets
                        if (totalProcessed % 50 == 0) {
                            log.info("Progress: {}/{} assets processed, {} saved, {} skipped, {} failed",
                                    totalProcessed, activeAssets.size(), totalSaved, totalSkipped, totalFailed);
                        }
                    }

                    log.info("US daily price update complete: {}/{} assets processed, {} saved, {} skipped, {} failed",
                            totalProcessed, activeAssets.size(), totalSaved, totalSkipped, totalFailed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}