package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
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
     * Find portfolio by ID and user ID (ownership validation)
     */
    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find portfolios by user and status
     */
    List<Portfolio> findByUserIdAndStatus(UUID userId, PortfolioStatus status);
}
