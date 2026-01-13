package com.porcana.batch.config;

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
 * <p>
 * Enables batch processing infrastructure and provides common batch settings
 * Includes scheduled jobs for weekly asset updates
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@EnableScheduling
public class BatchConfig {

    private final JobLauncher jobLauncher;
    private final Job krAssetJob;
    private final Job usAssetJob;
    private final Job krDailyPriceJob;
    private final Job usDailyPriceJob;

    public BatchConfig(
            JobLauncher jobLauncher,
            @Qualifier("krAssetJob") Job krAssetJob,
            @Qualifier("usAssetJob") Job usAssetJob,
            @Qualifier("krDailyPriceJob") Job krDailyPriceJob,
            @Qualifier("usDailyPriceJob")Job usDailyPriceJob
    ) {
        this.jobLauncher = jobLauncher;
        this.krAssetJob = krAssetJob;
        this.usAssetJob = usAssetJob;
        this.krDailyPriceJob = krDailyPriceJob;
        this.usDailyPriceJob = usDailyPriceJob;
    }

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
        } catch (Exception e) {
            log.error("Failed to run Korean market asset batch", e);
        }

        try{
            // Run US market batch
            log.info("Running US market asset batch");
            JobParameters usParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .toJobParameters();
            jobLauncher.run(usAssetJob, usParams);
            log.info("US market asset batch completed");

        } catch (Exception e) {
            log.error("Failed to run US market asset batch", e);
        }
    }

    /**
     * Scheduled daily price update job for Korean market
     * Runs every weekday at 18:00 KST (after market close at 15:30)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
    public void runKrDailyPriceUpdate() {
        log.info("Starting scheduled Korean daily price update");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "KR")
                    .toJobParameters();
            jobLauncher.run(krDailyPriceJob, params);
            log.info("Korean daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run Korean daily price update", e);
            // In production, you might want to send alerts here
        }
        log.info("Weekly asset update finished");
    }

    /**
     * Scheduled daily price update job for US market
     * Runs every weekday at 07:00 KST (after US market close)
     * Note: TUE-SAT in KST corresponds to MON-FRI in US Eastern Time
     */
    @Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void runUsDailyPriceUpdate() {
        log.info("Starting scheduled US daily price update");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .toJobParameters();
            jobLauncher.run(usDailyPriceJob, params);
            log.info("US daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run US daily price update", e);
            // In production, you might want to send alerts here
        }
    }
}