package com.porcana.batch.job;

import com.porcana.batch.provider.us.FmpAssetProvider;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
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

import java.util.List;

/**
 * Spring Batch job for updating image URLs of existing US assets
 *
 * Steps:
 * 1. Fetch all US STOCK assets
 * 2. For each asset, fetch image URL from FMP API
 * 3. Update imageUrl field
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsImageUpdateBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FmpAssetProvider fmpProvider;
    private final AssetRepository assetRepository;

    @Bean
    public Job usImageUpdateJob() {
        return new JobBuilder("usImageUpdateJob", jobRepository)
                .start(updateUsAssetImagesStep())
                .build();
    }

    /**
     * Step: Update image URLs for US stocks
     */
    @Bean
    public Step updateUsAssetImagesStep() {
        return new StepBuilder("updateUsAssetImagesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting US asset image update");

                    // Find all US STOCK assets
                    List<Asset> usAssets = assetRepository.findByMarketAndType(
                            Asset.Market.US, Asset.AssetType.STOCK);

                    log.info("Found {} US stock assets to update", usAssets.size());

                    int updated = 0;
                    int skipped = 0;
                    int failed = 0;

                    for (Asset asset : usAssets) {
                        try {
                            // Fetch image URL from FMP
                            String imageUrl = fmpProvider.fetchImageUrl(asset.getSymbol());

                            if (imageUrl != null && !imageUrl.isBlank()) {
                                asset.setImageUrl(imageUrl);
                                assetRepository.save(asset);
                                updated++;
                                log.debug("Updated image for {}: {}", asset.getSymbol(), imageUrl);
                            } else {
                                skipped++;
                                log.debug("No image URL found for {}", asset.getSymbol());
                            }

                            // Add delay to avoid rate limiting
                            Thread.sleep(150);

                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Image update interrupted at symbol: {}", asset.getSymbol());
                            break;
                        } catch (Exception e) {
                            failed++;
                            log.warn("Failed to update image for {}: {}", asset.getSymbol(), e.getMessage());
                        }

                        // Log progress every 50 assets
                        int total = updated + skipped + failed;
                        if (total % 50 == 0) {
                            log.info("Progress: {}/{} processed (updated: {}, skipped: {}, failed: {})",
                                    total, usAssets.size(), updated, skipped, failed);
                        }
                    }

                    log.info("US asset image update complete: {} updated, {} skipped, {} failed",
                            updated, skipped, failed);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}