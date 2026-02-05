package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.EtfProvider;
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
 * Spring Batch job for importing US ETFs from CSV
 *
 * Steps:
 * 1. Read ETF data from us_etf.csv
 * 2. Upsert to assets table (create or update)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsEtfBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EtfProvider etfProvider;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job usEtfJob() {
        return new JobBuilder("usEtfJob", jobRepository)
                .listener(batchNotificationListener)
                .start(importUsEtfsStep())
                .next(fetchUsEtfHistoricalPricesStep())
                .build();
    }

    /**
     * Step: Import US ETFs from CSV
     */
    @Bean
    public Step importUsEtfsStep() {
        return new StepBuilder("importUsEtfsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting US ETF import from CSV");

                    try {
                        List<AssetBatchDto> etfs = etfProvider.readUsEtfs();
                        log.info("Read {} ETFs from us_etf.csv", etfs.size());

                        int created = 0;
                        int updated = 0;

                        for (AssetBatchDto dto : etfs) {
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
                                log.debug("Updated ETF: {} ({})", dto.getSymbol(), dto.getName());
                            } else {
                                // Create new
                                assetRepository.save(dto.toEntity());
                                created++;
                                log.debug("Created ETF: {} ({})", dto.getSymbol(), dto.getName());
                            }
                        }

                        log.info("US ETF import complete: {} created, {} updated",
                                created, updated);

                    } catch (Exception e) {
                        log.error("Failed to import US ETFs", e);
                        throw new RuntimeException("US ETF import failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step: Fetch historical prices for recently created US ETFs
     * Fetches prices for ETFs created within the last 24 hours
     */
    @Bean
    public Step fetchUsEtfHistoricalPricesStep() {
        return new StepBuilder("fetchUsEtfHistoricalPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price fetch for US ETFs");

                    // Find ETFs created in the last 24 hours
                    LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
                    List<Asset> recentEtfs = assetRepository.findByMarketAndCreatedAtAfter(
                            Asset.Market.US, oneDayAgo);

                    // Filter only ETF type
                    List<Asset> recentEtfAssets = recentEtfs.stream()
                            .filter(asset -> asset.getType() == Asset.AssetType.ETF)
                            .toList();

                    log.info("Found {} US ETFs created in the last 24 hours", recentEtfAssets.size());

                    int totalPricesFetched = 0;
                    int etfsProcessed = 0;

                    for (Asset asset : recentEtfAssets) {
                        try {
                            // Check if historical prices already exist
                            boolean hasHistoricalData = assetPriceRepository.existsByAsset(asset);
                            if (hasHistoricalData) {
                                log.info("ETF {} already has historical price data, skipping", asset.getSymbol());
                                continue;
                            }

                            log.info("Fetching historical prices for ETF {}", asset.getSymbol());
                            List<AssetPrice> prices = fmpProvider.fetchHistoricalPrices(asset);

                            if (!prices.isEmpty()) {
                                // Save all prices at once
                                assetPriceRepository.saveAll(prices);
                                totalPricesFetched += prices.size();
                                etfsProcessed++;
                                log.info("Saved {} historical prices for ETF {}", prices.size(), asset.getSymbol());
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(150);

                        } catch (Exception e) {
                            log.warn("Failed to fetch historical prices for ETF {}: {}",
                                    asset.getSymbol(), e.getMessage());
                        }
                    }

                    log.info("Historical price fetch complete: {} prices saved for {} ETFs",
                            totalPricesFetched, etfsProcessed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
