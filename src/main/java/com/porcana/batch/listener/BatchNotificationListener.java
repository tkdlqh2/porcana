package com.porcana.batch.listener;

import com.porcana.global.notification.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Batch job execution listener that sends Discord notifications
 * Monitors all batch jobs and sends notifications on success/failure
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchNotificationListener implements JobExecutionListener {

    private final DiscordNotificationService discordNotificationService;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Starting batch job: {}", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        long durationMs = Duration.between(
                jobExecution.getStartTime(),
                jobExecution.getEndTime()
        ).toMillis();

        log.info("Batch job '{}' finished with status: {}, duration: {}ms", jobName, status, durationMs);

        if (status == BatchStatus.COMPLETED) {
            handleSuccess(jobExecution, jobName, durationMs);
        } else if (status == BatchStatus.FAILED) {
            handleFailure(jobExecution, jobName, durationMs);
        } else if (status == BatchStatus.STOPPED || status == BatchStatus.ABANDONED) {
            handleWarning(jobExecution, jobName);
        }
    }

    /**
     * Handle successful job execution
     */
    private void handleSuccess(JobExecution jobExecution, String jobName, long durationMs) {
        String summary = buildSuccessSummary(jobExecution);
        discordNotificationService.sendBatchSuccess(jobName, durationMs, summary);
    }

    /**
     * Handle failed job execution
     */
    private void handleFailure(JobExecution jobExecution, String jobName, long durationMs) {
        String errorMessage = buildErrorMessage(jobExecution);
        discordNotificationService.sendBatchFailure(jobName, durationMs, errorMessage);
    }

    /**
     * Handle stopped/abandoned job execution
     */
    private void handleWarning(JobExecution jobExecution, String jobName) {
        String message = String.format("Job status: %s", jobExecution.getStatus());
        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            message += "\nExceptions occurred during execution";
        }
        discordNotificationService.sendBatchWarning(jobName, message);
    }

    /**
     * Build success summary from job execution
     */
    private String buildSuccessSummary(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();

        if (stepExecutions.isEmpty()) {
            return "No steps executed";
        }

        StringBuilder summary = new StringBuilder();

        for (StepExecution step : stepExecutions) {
            summary.append(String.format("**%s**\n", step.getStepName()));
            summary.append(String.format("- Read: %d\n", step.getReadCount()));
            summary.append(String.format("- Write: %d\n", step.getWriteCount()));
            summary.append(String.format("- Skip: %d\n", step.getSkipCount()));

            if (step.getReadCount() > 0 || step.getWriteCount() > 0) {
                summary.append(String.format("- Commit: %d\n", step.getCommitCount()));
            }

            summary.append("\n");
        }

        return summary.toString().trim();
    }

    /**
     * Build error message from job execution
     */
    private String buildErrorMessage(JobExecution jobExecution) {
        List<Throwable> exceptions = jobExecution.getAllFailureExceptions();

        if (exceptions.isEmpty()) {
            return "Job failed without exception details";
        }

        StringBuilder errorMsg = new StringBuilder();

        // Get first exception (most relevant)
        Throwable firstException = exceptions.get(0);
        errorMsg.append(String.format("**%s**\n", firstException.getClass().getSimpleName()));
        errorMsg.append(String.format("```\n%s\n```\n", firstException.getMessage()));

        // Add step execution details
        for (StepExecution step : jobExecution.getStepExecutions()) {
            if (step.getStatus() == BatchStatus.FAILED) {
                errorMsg.append(String.format("\n**Failed Step:** %s\n", step.getStepName()));
                errorMsg.append(String.format("- Read: %d\n", step.getReadCount()));
                errorMsg.append(String.format("- Write: %d\n", step.getWriteCount()));
                errorMsg.append(String.format("- Skip: %d\n", step.getSkipCount()));

                if (!step.getFailureExceptions().isEmpty()) {
                    Throwable stepException = step.getFailureExceptions().get(0);
                    errorMsg.append(String.format("- Error: %s\n", stepException.getMessage()));
                }
            }
        }

        return errorMsg.toString();
    }
}