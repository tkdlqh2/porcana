package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.kr.DataGoKrAssetProvider;
import com.porcana.batch.provider.kr.UniverseTaggingProvider;
import com.porcana.batch.service.KrAssetDescriptionGenerator;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final KrAssetDescriptionGenerator krAssetDescriptionGenerator;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job krAssetJob() {
        return new JobBuilder("krAssetJob", jobRepository)
                .listener(batchNotificationListener)
                .start(fetchKrAssetsStep())
                .next(deactivateDelistedKrAssetsStep())
                .next(tagKospi200Step())
                .next(tagKosdaq150Step())
                .next(enrichKrDescriptionsStep())
                .next(fetchKrHistoricalPricesStep())
                .build();
    }

    private static final String FETCHED_KR_SYMBOLS_KEY = "fetchedKrSymbols";
    private static final int MIN_FETCHED_SYMBOLS_THRESHOLD = 10;

    /**
     * Step 1: Fetch all Korean stocks from data.go.kr
     * Saves fetched symbol set to JobExecutionContext for deactivate step
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
                        Set<String> fetchedSymbols = new HashSet<>();

                        for (AssetBatchDto dto : assets) {
                            fetchedSymbols.add(dto.getSymbol());
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

                        // Save fetched symbols to JobExecutionContext for deactivate step
                        chunkContext.getStepContext()
                                .getStepExecution()
                                .getJobExecution()
                                .getExecutionContext()
                                .put(FETCHED_KR_SYMBOLS_KEY, fetchedSymbols);

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
     * Step 2: Deactivate KR assets that are no longer listed on data.go.kr
     * Compares fetched symbols from previous step with active KR assets in DB
     */
    @Bean
    public Step deactivateDelistedKrAssetsStep() {
        return new StepBuilder("deactivateDelistedKrAssetsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting deactivation of delisted KR assets");

                    @SuppressWarnings("unchecked")
                    Set<String> fetchedSymbols = (Set<String>) chunkContext.getStepContext()
                            .getStepExecution()
                            .getJobExecution()
                            .getExecutionContext()
                            .get(FETCHED_KR_SYMBOLS_KEY);

                    if (fetchedSymbols == null || fetchedSymbols.size() < MIN_FETCHED_SYMBOLS_THRESHOLD) {
                        log.warn("Fetched symbols too few ({}). Skipping deactivation to avoid data loss.",
                                fetchedSymbols == null ? 0 : fetchedSymbols.size());
                        return RepeatStatus.FINISHED;
                    }

                    List<Asset> activeKrAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.KR);
                    log.info("Active KR assets in DB: {}, Fetched from data.go.kr: {}",
                            activeKrAssets.size(), fetchedSymbols.size());

                    int deactivated = 0;
                    for (Asset asset : activeKrAssets) {
                        if (!fetchedSymbols.contains(asset.getSymbol())) {
                            asset.deactivate();
                            assetRepository.save(asset);
                            deactivated++;
                            log.info("Deactivated delisted KR asset: {} ({})", asset.getSymbol(), asset.getName());
                        }
                    }

                    log.info("KR deactivation complete: {} assets deactivated", deactivated);
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
     * Step 4: Generate template descriptions for KR assets that do not have one yet
     */
    @Bean
    public Step enrichKrDescriptionsStep() {
        return new StepBuilder("enrichKrDescriptionsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting KR asset description enrichment");

                    List<Asset> activeKrAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.KR);
                    int updated = 0;

                    for (Asset asset : activeKrAssets) {
                        if (asset.getDescription() != null && !asset.getDescription().isBlank()) {
                            continue;
                        }

                        asset.setDescription(krAssetDescriptionGenerator.generate(asset));
                        updated++;
                    }

                    if (updated > 0) {
                        assetRepository.saveAll(activeKrAssets);
                    }

                    log.info("KR asset description enrichment complete: {} assets updated", updated);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Step 5: Fetch historical prices for recently created assets
     * Fetches prices for assets created within the last 24 hours
     */
    @Bean
    public Step fetchKrHistoricalPricesStep() {
        return new StepBuilder("fetchKrHistoricalPricesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting historical price fetch for Korean assets");

                    // Get timestamp from JobParameters
                    Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
                    Long timestamp = (Long) jobParameters.get("timestamp");
                    if (timestamp == null) {
                        timestamp = System.currentTimeMillis();
                        log.info("timestamp parameter is null, using current time: {}", timestamp);
                    }

                    // Convert timestamp to LocalDateTime (KST timezone)
                    LocalDateTime baseDateTime = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.of("Asia/Seoul"))
                            .toLocalDateTime();

                    // Find assets created in the last 24 hours from base time
                    LocalDateTime oneDayAgo = baseDateTime.minusDays(1);
                    log.info("Using base time: {}, searching for assets created after: {}", baseDateTime, oneDayAgo);

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
