package com.porcana.batch.runner;

import com.porcana.batch.provider.kr.DataGoKrAssetProvider;
import com.porcana.batch.provider.kr.DataGoKrEtfPriceProvider;
import com.porcana.batch.provider.us.FmpAssetProvider;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ApplicationRunner for backfilling asset price data with upsert logic
 *
 * This runner:
 * 1. Fetches historical price data for all active assets
 * 2. Upserts (insert if not exists, update if exists) price data
 * 3. Does NOT delete existing data
 *
 * Usage: Set batch.runner.price-backfill.enabled=true in application.yml
 * Default: false (disabled)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "batch.runner.price-backfill",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class AssetPriceBackfillRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final DataGoKrAssetProvider dataGoKrAssetProvider;
    private final DataGoKrEtfPriceProvider dataGoKrEtfPriceProvider;
    private final FmpAssetProvider fmpAssetProvider;

    private AssetPriceBackfillRunner self;

    @Autowired
    public void setSelf(@Lazy AssetPriceBackfillRunner self) {
        this.self = self;
    }

    // Backfill from February 1, 2025 to now
    private static final LocalDate BACKFILL_START_DATE = LocalDate.of(2025, 2, 1);

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("Starting Asset Price Backfill (Upsert) from {}", BACKFILL_START_DATE);
        log.info("========================================");

        // Get all active assets
        List<Asset> allAssets = assetRepository.findByActiveTrue();
        log.info("Found {} active assets to backfill", allAssets.size());

        // Separate by market
        List<Asset> krAssets = allAssets.stream()
                .filter(asset -> asset.getMarket() == Asset.Market.KR)
                .toList();

        List<Asset> usAssets = allAssets.stream()
                .filter(asset -> asset.getMarket() == Asset.Market.US)
                .toList();

        log.info("Korean assets: {}, US assets: {}", krAssets.size(), usAssets.size());

        // Backfill Korean assets
        backfillKoreanAssets(krAssets);

        // Backfill US assets
        backfillUsAssets(usAssets);

        log.info("========================================");
        log.info("Asset Price Backfill completed");
        log.info("========================================");
    }

    private void backfillKoreanAssets(List<Asset> krAssets) {
        log.info("========================================");
        log.info("Backfilling Korean assets");
        log.info("========================================");

        int total = krAssets.size();
        int success = 0;
        int failed = 0;
        int totalInserted = 0;
        int totalUpdated = 0;

        for (int i = 0; i < krAssets.size(); i++) {
            Asset asset = krAssets.get(i);
            log.info("[{}/{}] Fetching price data for {} ({})", i + 1, total, asset.getSymbol(), asset.getName());

            try {
                List<AssetPrice> prices;

                if (asset.getType() == Asset.AssetType.ETF) {
                    prices = dataGoKrEtfPriceProvider.fetchHistoricalPrices(asset);
                } else {
                    prices = dataGoKrAssetProvider.fetchHistoricalPrices(asset);
                }

                if (!prices.isEmpty()) {
                    // Filter to only include dates from BACKFILL_START_DATE onwards
                    List<AssetPrice> filteredPrices = prices.stream()
                            .filter(p -> !p.getPriceDate().isBefore(BACKFILL_START_DATE))
                            .toList();

                    if (!filteredPrices.isEmpty()) {
                        int[] result = self.upsertAssetPrices(asset, filteredPrices);
                        totalInserted += result[0];
                        totalUpdated += result[1];
                        success++;
                        log.info("  Upserted {} records ({} inserted, {} updated) for {}",
                                filteredPrices.size(), result[0], result[1], asset.getSymbol());
                    } else {
                        log.warn("  No data from {} onwards for {}", BACKFILL_START_DATE, asset.getSymbol());
                        failed++;
                    }
                } else {
                    log.warn("  No data fetched for {}", asset.getSymbol());
                    failed++;
                }

                // Rate limiting
                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Backfill interrupted at {}", asset.getSymbol());
                break;
            } catch (Exception e) {
                log.error("  Failed to backfill {}: {}", asset.getSymbol(), e.getMessage());
                failed++;
            }

            // Log progress
            if ((i + 1) % 10 == 0) {
                log.info("Progress: {}/{} processed, {} success, {} failed", i + 1, total, success, failed);
            }
        }

        log.info("Korean assets backfill complete: {} success, {} failed ({} inserted, {} updated)",
                success, failed, totalInserted, totalUpdated);
    }

    private void backfillUsAssets(List<Asset> usAssets) {
        log.info("========================================");
        log.info("Backfilling US assets");
        log.info("========================================");

        int total = usAssets.size();
        int success = 0;
        int failed = 0;
        int totalInserted = 0;
        int totalUpdated = 0;

        for (int i = 0; i < usAssets.size(); i++) {
            Asset asset = usAssets.get(i);
            log.info("[{}/{}] Fetching price data for {} ({})", i + 1, total, asset.getSymbol(), asset.getName());

            try {
                // FMP provider handles both stocks and ETFs
                List<AssetPrice> prices = fmpAssetProvider.fetchHistoricalPrices(asset);

                if (!prices.isEmpty()) {
                    // Filter to only include dates from BACKFILL_START_DATE onwards
                    List<AssetPrice> filteredPrices = prices.stream()
                            .filter(p -> !p.getPriceDate().isBefore(BACKFILL_START_DATE))
                            .toList();

                    if (!filteredPrices.isEmpty()) {
                        int[] result = self.upsertAssetPrices(asset, filteredPrices);
                        totalInserted += result[0];
                        totalUpdated += result[1];
                        success++;
                        log.info("  Upserted {} records ({} inserted, {} updated) for {}",
                                filteredPrices.size(), result[0], result[1], asset.getSymbol());
                    } else {
                        log.warn("  No data from {} onwards for {}", BACKFILL_START_DATE, asset.getSymbol());
                        failed++;
                    }
                } else {
                    log.warn("  No data fetched for {}", asset.getSymbol());
                    failed++;
                }

                // Rate limiting (FMP has stricter limits)
                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Backfill interrupted at {}", asset.getSymbol());
                break;
            } catch (Exception e) {
                log.error("  Failed to backfill {}: {}", asset.getSymbol(), e.getMessage());
                failed++;
            }

            // Log progress
            if ((i + 1) % 10 == 0) {
                log.info("Progress: {}/{} processed, {} success, {} failed", i + 1, total, success, failed);
            }
        }

        log.info("US assets backfill complete: {} success, {} failed ({} inserted, {} updated)",
                success, failed, totalInserted, totalUpdated);
    }

    /**
     * Upsert asset prices - insert if not exists, update if exists
     *
     * @return int[2] - [0] = inserted count, [1] = updated count
     */
    @Transactional
    protected int[] upsertAssetPrices(Asset asset, List<AssetPrice> prices) {
        int inserted = 0;
        int updated = 0;

        for (AssetPrice newPrice : prices) {
            var existingOpt = assetPriceRepository.findByAssetAndPriceDate(asset, newPrice.getPriceDate());

            if (existingOpt.isPresent()) {
                // Update existing record
                AssetPrice existing = existingOpt.get();
                existing.updatePrice(
                        newPrice.getOpenPrice(),
                        newPrice.getHighPrice(),
                        newPrice.getLowPrice(),
                        newPrice.getClosePrice(),
                        newPrice.getVolume()
                );
                assetPriceRepository.save(existing);
                updated++;
            } else {
                // Insert new record
                assetPriceRepository.save(newPrice);
                inserted++;
            }
        }

        return new int[]{inserted, updated};
    }
}