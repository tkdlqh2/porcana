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
 * Spring Batch job for importing US ETFs from CSV
 *
 * Steps:
 * 1. Read ETF data from us_etf.csv
 * 2. Upsert to assets table (create or update)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UsEtfBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EtfProvider etfProvider;
    private final AssetRepository assetRepository;

    @Bean
    public Job usEtfJob() {
        return new JobBuilder("usEtfJob", jobRepository)
                .start(importUsEtfsStep())
                .build();
    }

    /**
     * Step: Import US ETFs from CSV
     */
    @Bean
    public Step importUsEtfsStep() {
        return new StepBuilder("importUsEtfsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting US ETF import from CSV");

                    try {
                        List<AssetBatchDto> etfs = etfProvider.readUsEtfs();
                        log.info("Read {} ETFs from us_etf.csv", etfs.size());

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

                        log.info("US ETF import complete: {} created, {} updated",
                                created, updated);

                    } catch (Exception e) {
                        log.error("Failed to import US ETFs", e);
                        throw new RuntimeException("US ETF import failed", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
