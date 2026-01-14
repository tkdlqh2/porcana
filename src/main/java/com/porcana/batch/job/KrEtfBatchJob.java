package com.porcana.batch.job;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.batch.provider.EtfProvider;
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
    private final AssetRepository assetRepository;

    @Bean
    public Job krEtfJob() {
        return new JobBuilder("krEtfJob", jobRepository)
                .start(importKrEtfsStep())
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
}
