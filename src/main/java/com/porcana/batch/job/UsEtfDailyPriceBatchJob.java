package com.porcana.batch.job;

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

import java.util.List;

/**
 * Daily price update batch job for US ETFs
 * Fetches and updates latest EOD prices for all active US ETF assets
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsEtfDailyPriceBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job usEtfDailyPriceJob() {
        return new JobBuilder("usEtfDailyPriceJob", jobRepository)
                .listener(batchNotificationListener)
                .start(updateUsEtfDailyPricesStep())
                .build();
    }

    /**
     * Update daily prices for all active US ETF assets
     */
    @Bean
    public Step updateUsEtfDailyPricesStep() {
        return new StepBuilder("updateUsEtfDailyPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting daily price update for US ETFs");

                    // Find all active US ETF assets
                    List<Asset> activeEtfs = assetRepository.findByMarketAndTypeAndActiveTrue(
                            Asset.Market.US, Asset.AssetType.ETF);
                    log.info("Found {} active US ETFs", activeEtfs.size());

                    int totalProcessed = 0;
                    int totalSaved = 0;
                    int totalSkipped = 0;
                    int totalFailed = 0;

                    for (Asset asset : activeEtfs) {
                        try {
                            totalProcessed++;

                            // Fetch latest price using FMP provider (same as stocks)
                            AssetPrice latestPrice = fmpProvider.fetchDailyPrice(asset);

                            if (latestPrice == null) {
                                log.warn("No daily price data for ETF symbol: {}", asset.getSymbol());
                                totalFailed++;
                                continue;
                            }

                            // Check if price already exists for this date
                            boolean exists = assetPriceRepository.existsByAssetAndPriceDate(
                                    asset, latestPrice.getPriceDate());

                            if (exists) {
                                log.debug("Price already exists for ETF {} on {}, skipping",
                                        asset.getSymbol(), latestPrice.getPriceDate());
                                totalSkipped++;
                            } else {
                                // Save new price
                                assetPriceRepository.save(latestPrice);
                                log.info("Saved price for ETF {} on {}: ${}",
                                        asset.getSymbol(), latestPrice.getPriceDate(), latestPrice.getPrice());
                                totalSaved++;
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(150);

                        } catch (Exception e) {
                            log.error("Failed to update price for ETF symbol: {}", asset.getSymbol(), e);
                            totalFailed++;
                        }

                        // Log progress every 10 ETFs
                        if (totalProcessed % 10 == 0) {
                            log.info("Progress: {}/{} ETFs processed, {} saved, {} skipped, {} failed",
                                    totalProcessed, activeEtfs.size(), totalSaved, totalSkipped, totalFailed);
                        }
                    }

                    log.info("US ETF daily price update complete: {}/{} ETFs processed, {} saved, {} skipped, {} failed",
                            totalProcessed, activeEtfs.size(), totalSaved, totalSkipped, totalFailed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
