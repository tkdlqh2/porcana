package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    /**
     * Find all portfolios for a user
     */
    List<Portfolio> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find active and finished portfolios for a user (exclude DRAFT)
     */
    List<Portfolio> findByUserIdAndStatusNotOrderByCreatedAtDesc(UUID userId, PortfolioStatus status);

    /**
     * Find portfolio by ID and user ID (ownership validation)
     */
    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find portfolios by user and status
     */
    List<Portfolio> findByUserIdAndStatus(UUID userId, PortfolioStatus status);

    /**
     * Find portfolios by status (for batch processing)
     */
    List<Portfolio> findByStatus(PortfolioStatus status);

    /**
     * Find portfolios by status with pagination (for chunk-based batch processing)
     */
    Page<Portfolio> findByStatus(PortfolioStatus status, Pageable pageable);
}
