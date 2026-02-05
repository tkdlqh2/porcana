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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ApplicationRunner for backfilling OHLC data from 2025-01-13
 *
 * This runner:
 * 1. Deletes all price data from 2025-01-13 onwards
 * 2. Re-fetches historical OHLC data for all active assets
 *
 * Usage: Set batch.runner.ohlc-backfill.enabled=true in application.yml
 * Default: false (disabled)
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "batch.runner.ohlc-backfill",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
public class OhlcDataBackfillRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final DataGoKrAssetProvider dataGoKrAssetProvider;
    private final DataGoKrEtfPriceProvider dataGoKrEtfPriceProvider;
    private final FmpAssetProvider fmpAssetProvider;

    private static final LocalDate BACKFILL_START_DATE = LocalDate.of(2025, 2, 5);

    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("Starting OHLC Data Backfill from {}", BACKFILL_START_DATE);
        log.info("========================================");

        // Step 1: Delete existing data from backfill date onwards
        deleteExistingData();

        // Step 2: Fetch OHLC data for all active assets
        backfillOhlcData();

        log.info("========================================");
        log.info("OHLC Data Backfill completed");
        log.info("========================================");
    }

    protected void deleteExistingData() {
        log.info("Deleting price data from {} onwards...", BACKFILL_START_DATE);
        assetPriceRepository.deleteByPriceDateGreaterThanEqual(BACKFILL_START_DATE);
        log.info("Existing data deleted successfully");
    }

    protected void backfillOhlcData() {
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
    }

    private void backfillKoreanAssets(List<Asset> krAssets) {
        log.info("========================================");
        log.info("Backfilling Korean assets");
        log.info("========================================");

        int total = krAssets.size();
        int success = 0;
        int failed = 0;

        for (int i = 0; i < krAssets.size(); i++) {
            Asset asset = krAssets.get(i);
            log.info("[{}/{}] Fetching OHLC data for {} ({})", i + 1, total, asset.getSymbol(), asset.getName());

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
                        saveAssetPrices(filteredPrices);
                        success++;
                        log.info("  ✓ Saved {} price records for {}", filteredPrices.size(), asset.getSymbol());
                    } else {
                        log.warn("  ⚠ No data from {} onwards for {}", BACKFILL_START_DATE, asset.getSymbol());
                        failed++;
                    }
                } else {
                    log.warn("  ✗ No data fetched for {}", asset.getSymbol());
                    failed++;
                }

                // Rate limiting
                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Backfill interrupted at {}", asset.getSymbol());
                break;
            } catch (Exception e) {
                log.error("  ✗ Failed to backfill {}: {}", asset.getSymbol(), e.getMessage());
                failed++;
            }

            // Log progress
            if ((i + 1) % 10 == 0) {
                log.info("Progress: {}/{} processed, {} success, {} failed", i + 1, total, success, failed);
            }
        }

        log.info("Korean assets backfill complete: {} success, {} failed", success, failed);
    }

    private void backfillUsAssets(List<Asset> usAssets) {
        log.info("========================================");
        log.info("Backfilling US assets");
        log.info("========================================");

        int total = usAssets.size();
        int success = 0;
        int failed = 0;

        for (int i = 0; i < usAssets.size(); i++) {
            Asset asset = usAssets.get(i);
            log.info("[{}/{}] Fetching OHLC data for {} ({})", i + 1, total, asset.getSymbol(), asset.getName());

            try {
                List<AssetPrice> prices = fmpAssetProvider.fetchHistoricalPrices(asset);

                if (!prices.isEmpty()) {
                    // Filter to only include dates from BACKFILL_START_DATE onwards
                    List<AssetPrice> filteredPrices = prices.stream()
                            .filter(p -> !p.getPriceDate().isBefore(BACKFILL_START_DATE))
                            .toList();

                    if (!filteredPrices.isEmpty()) {
                        saveAssetPrices(filteredPrices);
                        success++;
                        log.info("  ✓ Saved {} price records for {}", filteredPrices.size(), asset.getSymbol());
                    } else {
                        log.warn("  ⚠ No data from {} onwards for {}", BACKFILL_START_DATE, asset.getSymbol());
                        failed++;
                    }
                } else {
                    log.warn("  ✗ No data fetched for {}", asset.getSymbol());
                    failed++;
                }

                // Rate limiting (FMP has stricter limits)
                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Backfill interrupted at {}", asset.getSymbol());
                break;
            } catch (Exception e) {
                log.error("  ✗ Failed to backfill {}: {}", asset.getSymbol(), e.getMessage());
                failed++;
            }

            // Log progress
            if ((i + 1) % 10 == 0) {
                log.info("Progress: {}/{} processed, {} success, {} failed", i + 1, total, success, failed);
            }
        }

        log.info("US assets backfill complete: {} success, {} failed", success, failed);
    }

    protected void saveAssetPrices(List<AssetPrice> prices) {
        assetPriceRepository.saveAll(prices);
    }
}