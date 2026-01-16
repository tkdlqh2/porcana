package com.porcana.batch.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for batch jobs
 * Executes batch jobs at scheduled intervals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job krAssetJob;
    private final Job usAssetJob;
    private final Job krEtfJob;
    private final Job usEtfJob;
    private final Job krDailyPriceJob;
    private final Job usDailyPriceJob;
    private final Job krEtfDailyPriceJob;
    private final Job usEtfDailyPriceJob;
    private final Job exchangeRateJob;
    private final Job assetRiskJob;

    /**
     * Execute Korean asset batch job every 30 minutes
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 1800000) // 30 minutes = 1,800,000 ms
    public void runKrAssetBatch() {
        try {
            log.info("Starting scheduled Korean asset batch job");

            // Use current timestamp to make each execution unique
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(krAssetJob, jobParameters);

            log.info("Korean asset batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute Korean asset batch job", e);
        }
    }

    /**
     * Execute US asset batch job every 30 minutes
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 1800000) // 30 minutes = 1,800,000 ms
    public void runUsAssetBatch() {
        try {
            log.info("Starting scheduled US asset batch job");

            // Use current timestamp to make each execution unique
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(usAssetJob, jobParameters);

            log.info("US asset batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute US asset batch job", e);
        }
    }

    /**
     * Execute Korean daily price update batch job every 24 hours
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runKrDailyPriceBatch() {
        try {
            log.info("Starting scheduled Korean daily price update batch job");

            // Use current timestamp to make each execution unique
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(krDailyPriceJob, jobParameters);

            log.info("Korean daily price update batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute Korean daily price update batch job", e);
        }
    }

    /**
     * Execute US daily price update batch job every 24 hours
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runUsDailyPriceBatch() {
        try {
            log.info("Starting scheduled US daily price update batch job");

            // Use current timestamp to make each execution unique
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(usDailyPriceJob, jobParameters);

            log.info("US daily price update batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute US daily price update batch job", e);
        }
    }

    /**
     * Execute Korean ETF batch job
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 1800000) // 30 minutes = 1,800,000 ms
    public void runKrEtfBatch() {
        try {
            log.info("Starting scheduled Korean ETF batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(krEtfJob, jobParameters);

            log.info("Korean ETF batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute Korean ETF batch job", e);
        }
    }

    /**
     * Execute US ETF batch job
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 1800000) // 30 minutes = 1,800,000 ms
    public void runUsEtfBatch() {
        try {
            log.info("Starting scheduled US ETF batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(usEtfJob, jobParameters);

            log.info("US ETF batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute US ETF batch job", e);
        }
    }

    /**
     * Execute Korean ETF daily price update batch job every 24 hours
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runKrEtfDailyPriceBatch() {
        try {
            log.info("Starting scheduled Korean ETF daily price update batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(krEtfDailyPriceJob, jobParameters);

            log.info("Korean ETF daily price update batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute Korean ETF daily price update batch job", e);
        }
    }

    /**
     * Execute US ETF daily price update batch job every 24 hours
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runUsEtfDailyPriceBatch() {
        try {
            log.info("Starting scheduled US ETF daily price update batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(usEtfDailyPriceJob, jobParameters);

            log.info("US ETF daily price update batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute US ETF daily price update batch job", e);
        }
    }

    /**
     * Execute exchange rate update batch job every 24 hours
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runExchangeRateBatch() {
        try {
            log.info("Starting scheduled exchange rate update batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(exchangeRateJob, jobParameters);

            log.info("Exchange rate update batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute exchange rate update batch job", e);
        }
    }

    /**
     * Execute asset risk calculation batch job every Sunday at 03:00 KST
     * Runs weekly after asset data update (02:00 KST)
     * Uncomment @Scheduled annotation to enable
     */
//    @Scheduled(fixedDelay = 86400000) // 24 hours = 86,400,000 ms
    public void runAssetRiskBatch() {
        try {
            log.info("Starting scheduled weekly asset risk calculation batch job");

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            jobLauncher.run(assetRiskJob, jobParameters);

            log.info("Weekly asset risk calculation batch job completed successfully");

        } catch (Exception e) {
            log.error("Failed to execute weekly asset risk calculation batch job", e);
        }
    }
}