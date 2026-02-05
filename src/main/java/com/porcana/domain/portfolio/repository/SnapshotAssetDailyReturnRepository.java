package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SnapshotAssetDailyReturnRepository extends JpaRepository<SnapshotAssetDailyReturn, UUID>,
        SnapshotAssetDailyReturnRepositoryCustom {

    /**
     * Find asset daily return for a specific portfolio, snapshot, asset, and date
     */
    Optional<SnapshotAssetDailyReturn> findByPortfolioIdAndSnapshotIdAndAssetIdAndReturnDate(
            UUID portfolioId, UUID snapshotId, UUID assetId, LocalDate returnDate);

    /**
     * Find all asset daily returns for a portfolio on a specific date
     */
    List<SnapshotAssetDailyReturn> findByPortfolioIdAndReturnDate(UUID portfolioId, LocalDate returnDate);

    /**
     * Find all asset daily returns for a snapshot on a specific date
     */
    List<SnapshotAssetDailyReturn> findBySnapshotIdAndReturnDate(UUID snapshotId, LocalDate returnDate);

    /**
     * Find all asset daily returns for a portfolio within date range
     */
    List<SnapshotAssetDailyReturn> findByPortfolioIdAndReturnDateBetweenOrderByReturnDateAsc(
            UUID portfolioId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all asset daily returns for a portfolio (ordered by date)
     */
    List<SnapshotAssetDailyReturn> findByPortfolioIdOrderByReturnDateAsc(UUID portfolioId);

    /**
     * Check if asset daily return exists
     */
    boolean existsByPortfolioIdAndSnapshotIdAndAssetIdAndReturnDate(
            UUID portfolioId, UUID snapshotId, UUID assetId, LocalDate returnDate);

    /**
     * Delete asset daily returns older than a specific date
     */
    void deleteByReturnDateBefore(LocalDate date);

    /**
     * Find all asset daily returns for a specific snapshot
     */
    List<SnapshotAssetDailyReturn> findBySnapshotIdOrderByReturnDateAsc(UUID snapshotId);

    /**
     * Find the most recent asset daily return for a specific portfolio and asset
     * (Used to get the latest market-cap based weight)
     */
    Optional<SnapshotAssetDailyReturn> findFirstByPortfolioIdAndAssetIdOrderByReturnDateDesc(
            UUID portfolioId, UUID assetId);

    /**
     * Find all asset daily returns for the most recent date of a portfolio
     */
    List<SnapshotAssetDailyReturn> findByPortfolioIdAndReturnDateOrderByAssetIdAsc(
            UUID portfolioId, LocalDate returnDate);
}