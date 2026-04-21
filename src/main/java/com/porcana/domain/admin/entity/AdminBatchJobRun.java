package com.porcana.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.batch.core.BatchStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "admin_batch_job_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBatchJobRun {

    @Id
    private UUID id;

    @Column(name = "batch_job_execution_id", nullable = false, unique = true)
    private Long batchJobExecutionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "issue_count", nullable = false)
    private Integer issueCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "batchJobRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AdminBatchJobIssue> issues = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (issueCount == null) {
            issueCount = 0;
        }
    }

    public void addIssue(AdminBatchJobIssue issue) {
        this.issues.add(issue);
        issue.setBatchJobRun(this);
    }
}
