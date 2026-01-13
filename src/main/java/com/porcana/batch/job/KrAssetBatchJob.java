package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.provider.kr.DataGoKrAssetProvider;
import com.porcana.batch.provider.kr.UniverseTaggingProvider;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.asset.entity.UniverseTag;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Spring Batch job for fetching and tagging Korean market assets
 *
 * Steps:
 * 1. Fetch all Korean stocks from data.go.kr API
 * 2. Tag KOSPI200 constituents from CSV
 * 3. Tag KOSDAQ150 constituents from CSV
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KrAssetBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataGoKrAssetProvider dataGoKrProvider;
    private final UniverseTaggingProvider taggingProvider;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;

    @Bean
    public Job krAssetJob() {
        return new JobBuilder("krAssetJob", jobRepository)
                .start(fetchKrAssetsStep())
                .next(tagKospi200Step())
                .next(tagKosdaq150Step())
                .next(fetchKrHistoricalPricesStep())
                .build();
    }

    /**
     * Step 1: Fetch all Korean stocks from data.go.kr
     */
    @Bean
    public Step fetchKrAssetsStep() {
        return new StepBuilder("fetchKrAssetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting Korean assets fetch from data.go.kr");

                    try {
                        List<AssetBatchDto> assets = dataGoKrProvider.fetchAssets();
                        log.info("Fetched {} assets from data.go.kr", assets.size());

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

                        log.info("Korean assets upsert complete: {} created, {} updated",
                                created, updated);

                    } catch (Exception e) {
                        log.error("Failed to fetch Korean assets", e);
                        throw new RuntimeException("Korean asset fetch failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 2: Tag KOSPI200 constituents
     */
    @Bean
    public Step tagKospi200Step() {
        return new StepBuilder("tagKospi200Step", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting KOSPI200 tagging");

                    Set<String> kospi200Symbols = taggingProvider.readKospi200Constituents();
                    log.info("Read {} KOSPI200 symbols from CSV", kospi200Symbols.size());

                    int[] tagged = {0};
                    for (String symbol : kospi200Symbols) {
                        assetRepository.findBySymbolAndMarket(symbol, Asset.Market.KR)
                                .ifPresent(asset -> {
                                    asset.addUniverseTag(UniverseTag.KOSPI200);
                                    asset.activate(); // KOSPI200 constituents are active
                                    assetRepository.save(asset);
                                    tagged[0]++;
                                });
                    }

                    log.info("KOSPI200 tagging complete: {} assets tagged", tagged[0]);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 3: Tag KOSDAQ150 constituents
     */
    @Bean
    public Step tagKosdaq150Step() {
        return new StepBuilder("tagKosdaq150Step", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting KOSDAQ150 tagging");

                    Set<String> kosdaq150Symbols = taggingProvider.readKosdaq150Constituents();
                    log.info("Read {} KOSDAQ150 symbols from CSV", kosdaq150Symbols.size());

                    int[] tagged = {0};
                    for (String symbol : kosdaq150Symbols) {
                        assetRepository.findBySymbolAndMarket(symbol, Asset.Market.KR)
                                .ifPresent(asset -> {
                                    asset.addUniverseTag(UniverseTag.KOSDAQ150);
                                    asset.activate(); // KOSDAQ150 constituents are active
                                    assetRepository.save(asset);
                                    tagged[0]++;
                                });
                    }

                    log.info("KOSDAQ150 tagging complete: {} assets tagged", tagged[0]);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 4: Fetch historical prices for recently created assets
     * Fetches prices for assets created within the last 24 hours
     */
    @Bean
    public Step fetchKrHistoricalPricesStep() {
        return new StepBuilder("fetchKrHistoricalPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price fetch for Korean assets");

                    // Find assets created in the last 24 hours
                    LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
                    List<Asset> recentAssets = assetRepository.findByMarketAndCreatedAtAfter(
                            Asset.Market.KR, oneDayAgo);

                    log.info("Found {} Korean assets created in the last 24 hours", recentAssets.size());

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
                            List<AssetPrice> prices = dataGoKrProvider.fetchHistoricalPrices(asset);

                            if (!prices.isEmpty()) {
                                // Save all prices at once
                                assetPriceRepository.saveAll(prices);
                                totalPricesFetched += prices.size();
                                assetsProcessed++;
                                log.info("Saved {} historical prices for {}", prices.size(), asset.getSymbol());
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(150);

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