package com.porcana.batch.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ApplicationRunner for US image update batch job
 *
 * Usage: Run the application and this runner will execute the usImageUpdateJob
 * To disable: Comment out @Component annotation
 */
@Slf4j
//@Component
@RequiredArgsConstructor
public class UsImageUpdateRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final Job usImageUpdateJob;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting US Image Update Runner");

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(usImageUpdateJob, jobParameters);

        log.info("US Image Update Runner completed");
    }
}