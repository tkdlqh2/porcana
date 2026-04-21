package com.porcana.batch.service;

import com.porcana.batch.support.BatchIssueCollector;
import com.porcana.domain.admin.entity.AdminBatchJobIssue;
import com.porcana.domain.admin.entity.AdminBatchJobRun;
import com.porcana.domain.admin.repository.AdminBatchJobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBatchLogService {

    private final AdminBatchJobRunRepository adminBatchJobRunRepository;
    private final BatchIssueCollector batchIssueCollector;

    @Transactional
    public void record(JobExecution jobExecution, String summary, String errorMessage) {
        List<BatchIssueCollector.CollectedIssue> issues = batchIssueCollector.drain(jobExecution.getId());

        Long durationMs = null;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            durationMs = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        }

        AdminBatchJobRun run = AdminBatchJobRun.builder()
                .batchJobExecutionId(jobExecution.getId())
                .jobName(jobExecution.getJobInstance().getJobName())
                .status(jobExecution.getStatus())
                .startedAt(jobExecution.getStartTime())
                .endedAt(jobExecution.getEndTime())
                .durationMs(durationMs)
                .summary(summary)
                .errorMessage(errorMessage)
                .issueCount(issues.size())
                .build();

        for (BatchIssueCollector.CollectedIssue issue : issues) {
            run.addIssue(AdminBatchJobIssue.builder()
                    .stepName(issue.getStepName())
                    .assetId(issue.getAssetId())
                    .assetSymbol(issue.getAssetSymbol())
                    .assetName(issue.getAssetName())
                    .issueCode(issue.getIssueCode())
                    .issueMessage(issue.getIssueMessage())
                    .severity(issue.getSeverity())
                    .build());
        }

        adminBatchJobRunRepository.save(run);
    }
}
