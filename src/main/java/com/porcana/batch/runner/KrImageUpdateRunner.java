package com.porcana.batch.runner;

import com.porcana.batch.provider.kr.KrAssetImageProvider;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class KrImageUpdateRunner implements ApplicationRunner {

    private final AssetRepository assetRepository;
    private final KrAssetImageProvider imageProvider;
    private final KrImageUpdateRunner self;
    private final boolean enabled;

    public KrImageUpdateRunner(
            AssetRepository assetRepository,
            KrAssetImageProvider imageProvider,
            @Value("${KR_IMAGE_UPDATE_ENABLED:false}") boolean enabled,
            @Lazy KrImageUpdateRunner self
    ) {
        this.assetRepository = assetRepository;
        this.imageProvider = imageProvider;
        this.enabled = enabled;
        this.self = self;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("KR image update is disabled. Set KR_IMAGE_UPDATE_ENABLED=true to enable.");
            return;
        }

        if (!imageProvider.isConfigured()) {
            log.warn("KR image provider is not configured. Image update skipped.");
            return;
        }

        log.info("Starting KR Image Update Runner");
        updateKrImages();
        log.info("KR Image Update Runner completed");
    }

    public void updateKrImages() {
        List<Asset> krAssets = assetRepository.findByMarketAndActiveTrue(Asset.Market.KR);
        int updated = 0;
        int skipped = 0;
        int missing = 0;
        int failed = 0;

        for (int i = 0; i < krAssets.size(); i++) {
            Asset asset = krAssets.get(i);

            if (hasImageUrl(asset)) {
                skipped++;
                continue;
            }

            try {
                var imageCandidate = imageProvider.findImage(asset);
                if (imageCandidate.isEmpty()) {
                    missing++;
                    continue;
                }

                self.updateSingleAsset(asset.getId(), imageCandidate.get().imageUrl());
                updated++;

                log.info("Updated KR image for {} from {}", asset.getSymbol(), imageCandidate.get().source());
            } catch (Exception e) {
                failed++;
                log.warn("Failed to update KR image for {}: {}", asset.getSymbol(), e.getMessage());
            }

            if ((i + 1) % 50 == 0) {
                log.info("KR image update progress: {}/{} processed, {} updated, {} skipped, {} missing, {} failed",
                        i + 1, krAssets.size(), updated, skipped, missing, failed);
            }
        }

        log.info("KR image update completed: {} total, {} updated, {} skipped, {} missing, {} failed",
                krAssets.size(), updated, skipped, missing, failed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateSingleAsset(UUID assetId, String imageUrl) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        asset.setImageUrl(imageUrl);
        assetRepository.save(asset);
    }

    private boolean hasImageUrl(Asset asset) {
        return asset.getImageUrl() != null && !asset.getImageUrl().isBlank();
    }
}
