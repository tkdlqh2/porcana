package com.porcana.batch.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Spring Batch configuration
 *
 * Enables batch processing infrastructure and provides common batch settings
 * Includes scheduled jobs for weekly asset updates
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@EnableScheduling
@RequiredArgsConstructor
public class BatchConfig {

    private final JobLauncher jobLauncher;

    @Qualifier("krAssetJob")
    private final Job krAssetJob;

    @Qualifier("usAssetJob")
    private final Job usAssetJob;

    /**
     * Scheduled asset update job - runs every Sunday at 2 AM
     * Updates both Korean and US market assets
     */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "Asia/Seoul")
    public void runWeeklyAssetUpdate() {
        log.info("Starting scheduled weekly asset update");

        try {
            // Run Korean market batch
            log.info("Running Korean market asset batch");
            JobParameters krParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "KR")
                    .toJobParameters();
            jobLauncher.run(krAssetJob, krParams);
            log.info("Korean market asset batch completed");

            // Run US market batch
            log.info("Running US market asset batch");
            JobParameters usParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .toJobParameters();
            jobLauncher.run(usAssetJob, usParams);
            log.info("US market asset batch completed");

            log.info("Weekly asset update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run weekly asset update", e);
            // In production, you might want to send alerts here
        }
    }
}