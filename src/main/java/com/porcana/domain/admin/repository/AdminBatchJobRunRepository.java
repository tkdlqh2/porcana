package com.porcana.domain.admin.repository;

import com.porcana.domain.admin.entity.AdminBatchJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminBatchJobRunRepository extends JpaRepository<AdminBatchJobRun, UUID> {
}
