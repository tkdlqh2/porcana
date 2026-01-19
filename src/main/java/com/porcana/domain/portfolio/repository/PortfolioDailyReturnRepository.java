package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioDailyReturnRepository extends JpaRepository<PortfolioDailyReturn, UUID> {

    /**
     * Find daily return for a portfolio on a specific date
     */
    Optional<PortfolioDailyReturn> findByPortfolioIdAndReturnDate(UUID portfolioId, LocalDate returnDate);

    /**
     * Find all daily returns for a portfolio within date range
     */
    List<PortfolioDailyReturn> findByPortfolioIdAndReturnDateBetweenOrderByReturnDateAsc(
            UUID portfolioId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all daily returns for a portfolio (ordered by date)
     */
    List<PortfolioDailyReturn> findByPortfolioIdOrderByReturnDateAsc(UUID portfolioId);

    /**
     * Find latest daily return for a portfolio
     */
    Optional<PortfolioDailyReturn> findFirstByPortfolioIdOrderByReturnDateDesc(UUID portfolioId);

    /**
     * Check if daily return exists for portfolio and date
     */
    boolean existsByPortfolioIdAndReturnDate(UUID portfolioId, LocalDate returnDate);

    /**
     * Delete daily returns older than a specific date
     */
    void deleteByReturnDateBefore(LocalDate date);

    /**
     * Find all daily returns for a specific snapshot
     */
    List<PortfolioDailyReturn> findBySnapshotIdOrderByReturnDateAsc(UUID snapshotId);
}