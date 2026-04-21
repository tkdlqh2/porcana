package com.porcana.domain.admin.repository;

import com.porcana.domain.admin.entity.AdminBatchJobIssue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdminBatchJobIssueRepository extends JpaRepository<AdminBatchJobIssue, UUID> {
    List<AdminBatchJobIssue> findByBatchJobRunIdOrderByCreatedAtDesc(UUID batchJobRunId);

    List<AdminBatchJobIssue> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
