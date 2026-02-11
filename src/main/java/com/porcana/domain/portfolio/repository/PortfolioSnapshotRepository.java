package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    /**
     * Find all snapshots for a portfolio (ordered by effective date)
     */
    List<PortfolioSnapshot> findByPortfolioIdOrderByEffectiveDateAsc(UUID portfolioId);

    /**
     * Find snapshot for a specific portfolio and effective date
     */
    Optional<PortfolioSnapshot> findByPortfolioIdAndEffectiveDate(UUID portfolioId, LocalDate effectiveDate);

    /**
     * Find the latest snapshot for a portfolio on or before a specific date
     */
    Optional<PortfolioSnapshot> findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            UUID portfolioId, LocalDate date);

    /**
     * Find the first (initial) snapshot for a portfolio
     */
    Optional<PortfolioSnapshot> findFirstByPortfolioIdOrderByEffectiveDateAsc(UUID portfolioId);

    /**
     * Check if snapshot exists for portfolio and effective date
     */
    boolean existsByPortfolioIdAndEffectiveDate(UUID portfolioId, LocalDate effectiveDate);

    /**
     * Find all snapshots for a portfolio
     * Used for hard-deleting portfolios
     */
    List<PortfolioSnapshot> findByPortfolioId(UUID portfolioId);

    /**
     * Delete all snapshots for a portfolio
     * Returns the number of deleted snapshots
     */
    int deleteByPortfolioId(UUID portfolioId);
}