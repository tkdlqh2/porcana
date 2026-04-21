package com.porcana.batch.runner;

import com.porcana.batch.provider.kr.DartApiProvider;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ApplicationRunner for updating KR dividend data from DART API
 *
 * Usage: Set KR_DIVIDEND_UPDATE_ENABLED=true as environment variable
 *
 * Example:
 *   KR_DIVIDEND_UPDATE_ENABLED=true ./gradlew bootRun
 *
 * Requires DART_API_KEY to be configured (batch.provider.dart.api-key)
 */
@Slf4j
@Component
public class KrDividendUpdateRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final DartApiProvider dartApiProvider;
    private final KrDividendUpdateRunner self;
    private final boolean enabled;

    public KrDividendUpdateRunner(
            AssetRepository assetRepository,
            AssetPriceRepository assetPriceRepository,
            DartApiProvider dartApiProvider,
            @Value("${KR_DIVIDEND_UPDATE_ENABLED:false}") boolean enabled,
            @Lazy KrDividendUpdateRunner self) {
        this.assetRepository = assetRepository;
        this.assetPriceRepository = assetPriceRepository;
        this.dartApiProvider = dartApiProvider;
        this.enabled = enabled;
        this.self = self;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("KR dividend data update is disabled. Set KR_DIVIDEND_UPDATE_ENABLED=true to enable.");
            return;
        }

        if (!dartApiProvider.isConfigured()) {
            log.warn("DART API key not configured. KR dividend update skipped.");
            return;
        }

        log.info("Starting KR Dividend Data Update Runner");

        try {
            updateKrDividendData();
            log.info("KR Dividend Data Update Runner completed successfully");
        } catch (Exception e) {
            log.error("KR Dividend Data Update Runner failed", e);
            throw e;
        }
    }

    /**
     * Main update loop - no transaction here to avoid long-running transaction
     */
    public void updateKrDividendData() {
        List<Asset> krAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.KR);
        log.info("Found {} active KR market assets to update dividend data", krAssets.size());

        int total = krAssets.size();
        int updated = 0;
        int failed = 0;
        int skipped = 0;

        for (int i = 0; i < krAssets.size(); i++) {
            Asset asset = krAssets.get(i);
            UUID assetId = asset.getId();
            String symbol = asset.getSymbol();

            try {
                log.info("Processing {}/{}: {}", i + 1, total, symbol);

                // 현재 주가 조회 (배당수익률 fallback 계산용)
                BigDecimal currentPrice = getLatestPrice(asset);

                DartApiProvider.DividendData dividendData = dartApiProvider.fetchDividendData(symbol, currentPrice);

                if (dividendData == null) {
                    log.warn("No dividend data returned for: {}", symbol);
                    skipped++;
                    continue;
                }

                self.updateSingleAsset(assetId, dividendData);
                updated++;

                log.info("Updated dividend data for {}: available={}, yield={}, frequency={}",
                        symbol,
                        dividendData.getDividendAvailable(),
                        dividendData.getDividendYield(),
                        dividendData.getDividendFrequency());

                // Rate limiting
                Thread.sleep(200);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("KR dividend update interrupted at symbol: {}", symbol);
                break;
            } catch (Exception e) {
                log.warn("Failed to update dividend data for {}: {}", symbol, e.getMessage());
                failed++;
            }

            if ((i + 1) % 50 == 0) {
                log.info("Progress: {}/{} processed, {} updated, {} failed, {} skipped",
                        i + 1, total, updated, failed, skipped);
            }
        }

        log.info("KR dividend data update completed: {} total, {} updated, {} failed, {} skipped",
                total, updated, failed, skipped);
    }

    /**
     * 종목의 최근 종가 조회
     */
    private BigDecimal getLatestPrice(Asset asset) {
        return assetPriceRepository.findFirstByAssetOrderByPriceDateDesc(asset)
                .map(AssetPrice::getClosePrice)
                .orElse(null);
    }

    /**
     * 단일 자산 배당 데이터 업데이트 (별도 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSingleAsset(UUID assetId, DartApiProvider.DividendData dividendData) {
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
