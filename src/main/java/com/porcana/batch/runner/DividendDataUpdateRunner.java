package com.porcana.batch.runner;

import com.porcana.batch.provider.us.FmpAssetProvider;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ApplicationRunner for updating dividend data from FMP API
 *
 * Usage: Set DIVIDEND_UPDATE_ENABLED=true in application.yml or as environment variable
 *
 * Example:
 *   DIVIDEND_UPDATE_ENABLED=true ./gradlew bootRun
 */
@Slf4j
@Component
public class DividendDataUpdateRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final FmpAssetProvider fmpAssetProvider;
    private final DividendDataUpdateRunner self;
    private final boolean enabled;

    public DividendDataUpdateRunner(
            AssetRepository assetRepository,
            FmpAssetProvider fmpAssetProvider,
            @Value("${DIVIDEND_UPDATE_ENABLED:false}") boolean enabled,
            @org.springframework.context.annotation.Lazy DividendDataUpdateRunner self) {
        this.assetRepository = assetRepository;
        this.fmpAssetProvider = fmpAssetProvider;
        this.enabled = enabled;
        this.self = self;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("Dividend data update is disabled. Set DIVIDEND_UPDATE_ENABLED=true to enable.");
            return;
        }

        log.info("Starting Dividend Data Update Runner");

        try {
            updateDividendData();
            log.info("Dividend Data Update Runner completed successfully");
        } catch (Exception e) {
            log.error("Dividend Data Update Runner failed", e);
            throw e;
        }
    }

    /**
     * Main update loop - no transaction here to avoid long-running transaction
     */
    public void updateDividendData() {
        // Fetch all active US market asset IDs (read-only, quick query)
        List<Asset> usAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.US);
        log.info("Found {} active US market assets to update dividend data", usAssets.size());

        int total = usAssets.size();
        int updated = 0;
        int failed = 0;
        int skipped = 0;

        for (int i = 0; i < usAssets.size(); i++) {
            Asset asset = usAssets.get(i);
            UUID assetId = asset.getId();
            String symbol = asset.getSymbol();

            try {
                log.info("Processing {}/{}: {}", i + 1, total, symbol);

                FmpAssetProvider.DividendData dividendData = fmpAssetProvider.fetchDividendData(symbol);

                if (dividendData == null) {
                    log.warn("No dividend data returned for: {}", symbol);
                    skipped++;
                    continue;
                }

                // Update in separate transaction (short-lived)
                self.updateSingleAsset(assetId, dividendData);
                updated++;

                log.info("Updated dividend data for {}: yield={}, frequency={}, category={}",
                        symbol,
                        dividendData.getDividendYield(),
                        dividendData.getDividendFrequency(),
                        dividendData.getDividendCategory());

                // Rate limiting - 150ms delay between API calls
                Thread.sleep(150);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Dividend update interrupted at symbol: {}", symbol);
                break;
            } catch (Exception e) {
                log.warn("Failed to update dividend data for {}: {}", symbol, e.getMessage());
                failed++;
            }

            // Log progress every 50 symbols
            if ((i + 1) % 50 == 0) {
                log.info("Progress: {}/{} processed, {} updated, {} failed, {} skipped",
                        i + 1, total, updated, failed, skipped);
            }
        }

        log.info("Dividend data update completed: {} total, {} updated, {} failed, {} skipped",
                total, updated, failed, skipped);
    }

    /**
     * Update single asset in its own transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSingleAsset(UUID assetId, FmpAssetProvider.DividendData dividendData) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        asset.updateDividendData(
                dividendData.getDividendAvailable(),
                dividendData.getDividendYield(),
                dividendData.getDividendFrequency(),
                dividendData.getDividendCategory(),
                dividendData.getDividendDataStatus(),
                dividendData.getLastDividendDate()
        );

        assetRepository.save(asset);
    }
}