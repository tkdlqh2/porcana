package com.porcana.domain.admin.dto.response;

import com.porcana.domain.admin.entity.AdminBatchJobIssue;
import com.porcana.domain.admin.entity.BatchIssueSeverity;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminTodayBatchIssueListResponse(
        List<IssueItem> issues
) {
    @Builder
    public record IssueItem(
            UUID issueId,
            String jobName,
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
                    .jobName(issue.getBatchJobRun().getJobName())
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

    public static AdminTodayBatchIssueListResponse from(List<AdminBatchJobIssue> issues) {
        return AdminTodayBatchIssueListResponse.builder()
                .issues(issues.stream().map(IssueItem::from).toList())
                .build();
    }
}
