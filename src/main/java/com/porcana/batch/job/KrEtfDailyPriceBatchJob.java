package com.porcana.batch.job;

import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.kr.DataGoKrEtfPriceProvider;
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
 * Daily price update batch job for Korean ETFs
 * Fetches and updates latest EOD prices for all active Korean ETF assets
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KrEtfDailyPriceBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataGoKrEtfPriceProvider etfPriceProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job krEtfDailyPriceJob() {
        return new JobBuilder("krEtfDailyPriceJob", jobRepository)
                .listener(batchNotificationListener)
                .start(updateKrEtfDailyPricesStep())
                .build();
    }

    /**
     * Update daily prices for all active Korean ETF assets
     */
    @Bean
    public Step updateKrEtfDailyPricesStep() {
        return new StepBuilder("updateKrEtfDailyPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting daily price update for Korean ETFs");

                    // Find all active Korean ETF assets
                    List<Asset> activeEtfs = assetRepository.findByMarketAndTypeAndActiveTrue(
                            Asset.Market.KR, Asset.AssetType.ETF);
                    log.info("Found {} active Korean ETFs", activeEtfs.size());

                    int totalProcessed = 0;
                    int totalSaved = 0;
                    int totalSkipped = 0;
                    int totalFailed = 0;

                    for (Asset asset : activeEtfs) {
                        try {
                            totalProcessed++;

                            // Fetch latest price
                            AssetPrice latestPrice = etfPriceProvider.fetchDailyPrice(asset);

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
                                log.info("Saved price for ETF {} on {}: ₩{}",
                                        asset.getSymbol(), latestPrice.getPriceDate(), latestPrice.getPrice());
                                totalSaved++;
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(100);

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

                    log.info("Korean ETF daily price update complete: {}/{} ETFs processed, {} saved, {} skipped, {} failed",
                            totalProcessed, activeEtfs.size(), totalSaved, totalSkipped, totalFailed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
