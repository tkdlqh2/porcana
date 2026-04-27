package com.porcana.batch.job;

import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.provider.us.WikipediaUniverseProvider;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monthly job that synchronizes the US stock universe.
 *
 * Responsibility: "Which symbols should we track?"
 * - S&P 500 / NASDAQ 100: fetched from Wikipedia (change quarterly / annually)
 * - Dow Jones 30: read from dowjones.csv (changes only a few times per decade — update manually)
 *
 * New symbols are added as inactive; UsAssetBatchJob (weekly) activates them after FMP confirmation.
 * Schedule: 1st Sunday of each month, 01:00 KST
 */
@Slf4j
@Configuration("usUniverseSyncJobConfig")
@RequiredArgsConstructor
public class UsUniverseSyncJob {

    private static final String DOW30_CSV = "batch/dowjones.csv";

    /** Index tags managed by this job — used to detect removals on rebalancing. */
    private static final List<UniverseTag> INDEX_TAGS =
            List.of(UniverseTag.SP500, UniverseTag.NASDAQ100, UniverseTag.DOW30);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final WikipediaUniverseProvider wikipediaProvider;
    private final AssetRepository assetRepository;
    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job usUniverseSyncJob() {
        return new JobBuilder("usUniverseSyncJob", jobRepository)
                .listener(batchNotificationListener)
                .start(syncUsUniverseStep())
                .build();
    }

    @Bean
    public Step syncUsUniverseStep() {
        return new StepBuilder("syncUsUniverseStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting US universe sync");

                    // S&P 500 and NASDAQ 100 from Wikipedia (frequent rebalancing)
                    Set<String> sp500  = wikipediaProvider.fetchSp500Symbols();
                    Set<String> nasdaq = wikipediaProvider.fetchNasdaq100Symbols();
                    // Dow Jones 30 from CSV (rarely changes — update CSV manually when needed)
                    Set<String> dow30  = readDow30FromCsv();

                    log.info("Universe sources: S&P500={} (Wikipedia), NASDAQ100={} (Wikipedia), Dow30={} (CSV)",
                            sp500.size(), nasdaq.size(), dow30.size());

                    // Build symbol → expected tags map
                    Map<String, List<UniverseTag>> symbolToTags = new HashMap<>();
                    sp500.forEach(s  -> symbolToTags.computeIfAbsent(s, k -> new ArrayList<>()).add(UniverseTag.SP500));
                    nasdaq.forEach(s -> symbolToTags.computeIfAbsent(s, k -> new ArrayList<>()).add(UniverseTag.NASDAQ100));
                    dow30.forEach(s  -> symbolToTags.computeIfAbsent(s, k -> new ArrayList<>()).add(UniverseTag.DOW30));

                    if (symbolToTags.isEmpty()) {
                        log.warn("All universe sources returned empty. Skipping sync to avoid data loss.");
                        return RepeatStatus.FINISHED;
                    }

                    int created = 0;
                    int tagsUpdated = 0;

                    for (Map.Entry<String, List<UniverseTag>> entry : symbolToTags.entrySet()) {
                        String symbol = entry.getKey();
                        List<UniverseTag> expectedTags = entry.getValue();

                        var existing = assetRepository.findBySymbolAndMarket(symbol, Asset.Market.US);

                        if (existing.isPresent()) {
                            Asset asset = existing.get();
                            List<UniverseTag> currentTags = new ArrayList<>(asset.getUniverseTags());
                            boolean changed = false;

                            // Add newly acquired index tags
                            for (UniverseTag tag : expectedTags) {
                                if (!currentTags.contains(tag)) {
                                    currentTags.add(tag);
                                    changed = true;
                                }
                            }
                            // Remove index tags that no longer apply (e.g. dropped from S&P 500)
                            for (UniverseTag indexTag : INDEX_TAGS) {
                                if (!expectedTags.contains(indexTag) && currentTags.contains(indexTag)) {
                                    currentTags.remove(indexTag);
                                    changed = true;
                                }
                            }

                            if (changed) {
                                asset.updateUniverseTags(currentTags);
                                asset.updateAsOf(LocalDate.now());
                                assetRepository.save(asset);
                                tagsUpdated++;
                            }

                        } else {
                            // New symbol — inactive until weekly status check confirms via FMP
                            Asset newAsset = Asset.builder()
                                    .market(Asset.Market.US)
                                    .symbol(symbol)
                                    .name(symbol)       // placeholder; FMP status check fills the real name
                                    .type(Asset.AssetType.STOCK)
                                    .universeTags(expectedTags)
                                    .active(false)
                                    .asOf(LocalDate.now())
                                    .build();
                            assetRepository.save(newAsset);
                            created++;
                        }
                    }

                    log.info("Universe sync complete: {} new symbols added (inactive), {} tag updates",
                            created, tagsUpdated);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Reads Dow Jones 30 symbols from the bundled CSV.
     * Format: one symbol per line, with an optional header row starting with "symbol".
     * Lines starting with '#' are treated as comments.
     *
     * Update dowjones.csv manually when a constituent change is announced.
     */
    private Set<String> readDow30FromCsv() {
        Set<String> symbols = new LinkedHashSet<>();
        ClassPathResource resource = new ClassPathResource(DOW30_CSV);

        if (!resource.exists()) {
            log.warn("{} not found on classpath — Dow Jones 30 will be skipped this sync", DOW30_CSV);
            return symbols;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (firstLine) {
                    firstLine = false;
                    if (line.equalsIgnoreCase("symbol")) continue; // skip header
                }
                if (line.isEmpty() || line.startsWith("#")) continue;
                symbols.add(line.split(",")[0].trim().toUpperCase());
            }

        } catch (IOException e) {
            log.error("Failed to read {}", DOW30_CSV, e);
        }

        log.info("Read {} Dow Jones 30 symbols from {}", symbols.size(), DOW30_CSV);
        return symbols;
    }
}