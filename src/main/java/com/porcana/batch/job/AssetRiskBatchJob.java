package com.porcana.batch.job;

import com.porcana.batch.service.risk.AssetRiskService;
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

/**
 * Weekly asset risk calculation batch job
 * Calculates risk levels for all active assets based on:
 * - Volatility (60-day annualized standard deviation)
 * - Max Drawdown (252-day maximum drawdown)
 * - Worst Day Return (252-day minimum daily return)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AssetRiskBatchJob {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AssetRiskService assetRiskService;

    @Bean
    public Job assetRiskJob() {
        return new JobBuilder("assetRiskJob", jobRepository)
                .start(calculateAssetRisksStep())
                .build();
    }

    /**
     * Calculate and save risk metrics for all active assets
     */
    @Bean
    public Step calculateAssetRisksStep() {
        return new StepBuilder("calculateAssetRisksStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Starting weekly asset risk calculation");

                    try {
                        assetRiskService.calculateAndSaveAllAssetRisks();
                        log.info("Weekly asset risk calculation completed successfully");
                    } catch (Exception e) {
                        log.error("Failed to calculate asset risks", e);
                        throw e;
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}