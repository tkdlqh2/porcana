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
}