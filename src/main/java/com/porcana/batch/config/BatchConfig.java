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
 * Includes scheduled jobs for:
 * - Weekly asset updates (stocks and ETFs)
 * - Daily price updates (stocks and ETFs)
 * - Daily exchange rate updates
 */
@Slf4j
@Configuration
@EnableBatchProcessing
@EnableScheduling
public class BatchConfig {

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
    private final Job portfolioPerformanceJob;

    public BatchConfig(
            JobLauncher jobLauncher,
            @Qualifier("krAssetJob") Job krAssetJob,
            @Qualifier("usAssetJob") Job usAssetJob,
            @Qualifier("krEtfJob") Job krEtfJob,
            @Qualifier("usEtfJob") Job usEtfJob,
            @Qualifier("krDailyPriceJob") Job krDailyPriceJob,
            @Qualifier("usDailyPriceJob") Job usDailyPriceJob,
            @Qualifier("krEtfDailyPriceJob") Job krEtfDailyPriceJob,
            @Qualifier("usEtfDailyPriceJob") Job usEtfDailyPriceJob,
            @Qualifier("exchangeRateJob") Job exchangeRateJob,
            @Qualifier("assetRiskJob") Job assetRiskJob,
            @Qualifier("portfolioPerformanceJob") Job portfolioPerformanceJob
    ) {
        this.jobLauncher = jobLauncher;
        this.krAssetJob = krAssetJob;
        this.usAssetJob = usAssetJob;
        this.krEtfJob = krEtfJob;
        this.usEtfJob = usEtfJob;
        this.krDailyPriceJob = krDailyPriceJob;
        this.usDailyPriceJob = usDailyPriceJob;
        this.krEtfDailyPriceJob = krEtfDailyPriceJob;
        this.usEtfDailyPriceJob = usEtfDailyPriceJob;
        this.exchangeRateJob = exchangeRateJob;
        this.assetRiskJob = assetRiskJob;
        this.portfolioPerformanceJob = portfolioPerformanceJob;
    }

    /**
     * Scheduled asset update job - runs every Sunday at 2 AM
     * Updates both Korean and US market assets (stocks and ETFs)
     */
    @Scheduled(cron = "0 0 2 * * SUN", zone = "Asia/Seoul")
    public void runWeeklyAssetUpdate() {
        log.info("Starting scheduled weekly asset update");

        try {
            // Run Korean market stock batch
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

        try {
            // Run Korean market ETF batch
            log.info("Running Korean market ETF batch");
            JobParameters krEtfParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "KR")
                    .addString("type", "ETF")
                    .toJobParameters();
            jobLauncher.run(krEtfJob, krEtfParams);
            log.info("Korean market ETF batch completed");
        } catch (Exception e) {
            log.error("Failed to run Korean market ETF batch", e);
        }

        try{
            // Run US market stock batch
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

        try {
            // Run US market ETF batch
            log.info("Running US market ETF batch");
            JobParameters usEtfParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .addString("type", "ETF")
                    .toJobParameters();
            jobLauncher.run(usEtfJob, usEtfParams);
            log.info("US market ETF batch completed");
        } catch (Exception e) {
            log.error("Failed to run US market ETF batch", e);
        }
    }

    /**
     * Scheduled daily price update job for Korean market (stocks and ETFs)
     * Runs every weekday at 18:00 KST (after market close at 15:30)
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
    public void runKrDailyPriceUpdate() {
        log.info("Starting scheduled Korean daily price update");

        try {
            // Update stock prices
            JobParameters stockParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "KR")
                    .addString("type", "STOCK")
                    .toJobParameters();
            jobLauncher.run(krDailyPriceJob, stockParams);
            log.info("Korean stock daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run Korean stock daily price update", e);
            // In production, you might want to send alerts here
        }

        try {
            // Update ETF prices
            JobParameters etfParams = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("market", "KR")
                    .addString("type", "ETF")
                    .toJobParameters();
            jobLauncher.run(krEtfDailyPriceJob, etfParams);
            log.info("Korean ETF daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run Korean ETF daily price update", e);
            // In production, you might want to send alerts here
        }
    }

    /**
     * Scheduled daily price update job for US market (stocks and ETFs)
     * Runs every weekday at 07:00 KST (after US market close)
     * Note: TUE-SAT in KST corresponds to MON-FRI in US Eastern Time
     */
    @Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
    public void runUsDailyPriceUpdate() {
        log.info("Starting scheduled US daily price update");

        try {
            // Update stock prices
            JobParameters stockParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .addString("type", "STOCK")
                    .toJobParameters();
            jobLauncher.run(usDailyPriceJob, stockParams);
            log.info("US stock daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run US stock daily price update", e);
            // In production, you might want to send alerts here
        }

        try {
            // Update ETF prices
            JobParameters etfParams = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("market", "US")
                    .addString("type", "ETF")
                    .toJobParameters();
            jobLauncher.run(usEtfDailyPriceJob, etfParams);
            log.info("US ETF daily price update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run US ETF daily price update", e);
            // In production, you might want to send alerts here
        }
    }

    /**
     * Scheduled exchange rate update job
     * Runs every weekday at 12:00 KST
     * Korea Exim Bank updates exchange rates around 10:00 KST
     */
    @Scheduled(cron = "0 0 12 * * MON-FRI", zone = "Asia/Seoul")
    public void runExchangeRateUpdate() {
        log.info("Starting scheduled exchange rate update");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(exchangeRateJob, params);
            log.info("Exchange rate update completed successfully");

        } catch (Exception e) {
            log.error("Failed to run exchange rate update", e);
            // In production, you might want to send alerts here
        }
    }

    /**
     * Execute asset risk calculation batch job every Sunday at 03:00 KST
     * Runs weekly after asset data update (02:00 KST)
     * Uncomment @Scheduled annotation to enable
     */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Asia/Seoul")
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

    /**
     * Scheduled portfolio performance calculation job
     * Runs every day at 08:00 KST (after US market price update at 07:00)
     * Calculates daily returns for all ACTIVE portfolios
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void runPortfolioPerformanceCalculation() {
        log.info("Starting scheduled portfolio performance calculation");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(portfolioPerformanceJob, params);
            log.info("Portfolio performance calculation completed successfully");

        } catch (Exception e) {
            log.error("Failed to run portfolio performance calculation", e);
            // In production, you might want to send alerts here
        }
    }
}