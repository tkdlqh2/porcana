package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.provider.EtfProvider;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Batch job for importing Korean ETFs from CSV
 *
 * Steps:
 * 1. Read ETF data from kr_etf.csv
 * 2. Upsert to assets table (create or update)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KrEtfBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EtfProvider etfProvider;
    private final DataGoKrEtfPriceProvider etfPriceProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;

    @Bean
    public Job krEtfJob() {
        return new JobBuilder("krEtfJob", jobRepository)
                .start(importKrEtfsStep())
                .next(fetchKrEtfHistoricalPricesStep())
                .build();
    }

    /**
     * Step: Import Korean ETFs from CSV
     */
    @Bean
    public Step importKrEtfsStep() {
        return new StepBuilder("importKrEtfsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting Korean ETF import from CSV");

                    try {
                        List<AssetBatchDto> etfs = etfProvider.readKrEtfs();
                        log.info("Read {} ETFs from kr_etf.csv", etfs.size());

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

                        log.info("Korean ETF import complete: {} created, {} updated",
                                created, updated);

                    } catch (Exception e) {
                        log.error("Failed to import Korean ETFs", e);
                        throw new RuntimeException("Korean ETF import failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step: Fetch historical prices for recently created Korean ETFs
     * Fetches prices for ETFs created within the last 24 hours
     */
    @Bean
    public Step fetchKrEtfHistoricalPricesStep() {
        return new StepBuilder("fetchKrEtfHistoricalPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price fetch for Korean ETFs");

                    // Find ETFs created in the last 24 hours
                    LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
                    List<Asset> recentEtfs = assetRepository.findByMarketAndCreatedAtAfter(
                            Asset.Market.KR, oneDayAgo);

                    // Filter only ETF type
                    List<Asset> recentEtfAssets = recentEtfs.stream()
                            .filter(asset -> asset.getType() == Asset.AssetType.ETF)
                            .toList();

                    log.info("Found {} Korean ETFs created in the last 24 hours", recentEtfAssets.size());

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
                            List<AssetPrice> prices = etfPriceProvider.fetchHistoricalPrices(asset);

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
