package com.porcana.domain.admin.dto.response;

import com.porcana.domain.admin.entity.AdminBatchJobRun;
import lombok.Builder;
import org.springframework.batch.core.BatchStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminBatchRunListResponse(
        List<BatchRunItem> runs,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    @Builder
    public record BatchRunItem(
            UUID runId,
            Long batchJobExecutionId,
            String jobName,
            BatchStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Long durationMs,
            Integer issueCount,
            String summary
    ) {
        public static BatchRunItem from(AdminBatchJobRun run) {
            return BatchRunItem.builder()
                    .runId(run.getId())
                    .batchJobExecutionId(run.getBatchJobExecutionId())
                    .jobName(run.getJobName())
                    .status(run.getStatus())
                    .startedAt(run.getStartedAt())
                    .endedAt(run.getEndedAt())
                    .durationMs(run.getDurationMs())
                    .issueCount(run.getIssueCount())
                    .summary(run.getSummary())
                    .build();
        }
    }

    public static AdminBatchRunListResponse from(Page<AdminBatchJobRun> page) {
        return AdminBatchRunListResponse.builder()
                .runs(page.getContent().stream().map(BatchRunItem::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
