package com.porcana.domain.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_batch_job_issues")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBatchJobIssue {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_job_run_id", nullable = false)
    private AdminBatchJobRun batchJobRun;

    @Column(name = "step_name", length = 100)
    private String stepName;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "asset_symbol", length = 20)
    private String assetSymbol;

    @Column(name = "asset_name")
    private String assetName;

    @Column(name = "issue_code", nullable = false, length = 100)
    private String issueCode;

    @Column(name = "issue_message", nullable = false, columnDefinition = "TEXT")
    private String issueMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchIssueSeverity severity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
