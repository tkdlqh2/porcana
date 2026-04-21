package com.porcana.domain.admin.dto.response;

import com.porcana.domain.admin.entity.AdminBatchJobIssue;
import com.porcana.domain.admin.entity.AdminBatchJobRun;
import com.porcana.domain.admin.entity.BatchIssueSeverity;
import lombok.Builder;
import org.springframework.batch.core.BatchStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminBatchRunDetailResponse(
        UUID runId,
        Long batchJobExecutionId,
        String jobName,
        BatchStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationMs,
        Integer issueCount,
        String summary,
        String errorMessage,
        List<IssueItem> issues
) {
    @Builder
    public record IssueItem(
            UUID issueId,
            String stepName,
            UUID assetId,
            String assetSymbol,
            String assetName,
            String issueCode,
            String issueMessage,
            BatchIssueSeverity severity,
            LocalDateTime createdAt
    ) {
        public static IssueItem from(AdminBatchJobIssue issue) {
            return IssueItem.builder()
                    .issueId(issue.getId())
                    .stepName(issue.getStepName())
                    .assetId(issue.getAssetId())
                    .assetSymbol(issue.getAssetSymbol())
                    .assetName(issue.getAssetName())
                    .issueCode(issue.getIssueCode())
                    .issueMessage(issue.getIssueMessage())
                    .severity(issue.getSeverity())
                    .createdAt(issue.getCreatedAt())
                    .build();
        }
    }

    public static AdminBatchRunDetailResponse from(AdminBatchJobRun run, List<AdminBatchJobIssue> issues) {
        return AdminBatchRunDetailResponse.builder()
                .runId(run.getId())
                .batchJobExecutionId(run.getBatchJobExecutionId())
                .jobName(run.getJobName())
                .status(run.getStatus())
                .startedAt(run.getStartedAt())
                .endedAt(run.getEndedAt())
                .durationMs(run.getDurationMs())
                .issueCount(run.getIssueCount())
                .summary(run.getSummary())
                .errorMessage(run.getErrorMessage())
                .issues(issues.stream().map(IssueItem::from).toList())
                .build();
    }
}
