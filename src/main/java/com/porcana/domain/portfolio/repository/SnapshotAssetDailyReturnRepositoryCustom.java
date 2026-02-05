package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.dto.PortfolioListResponse;

import java.util.List;
import java.util.UUID;

/**
 * Custom repository for SnapshotAssetDailyReturn
 * Provides QueryDSL-based queries
 */
public interface SnapshotAssetDailyReturnRepositoryCustom {

    /**
     * Find top N assets by weight for the most recent date of a portfolio
     * Uses latest market-cap based weights (weightUsed)
     *
     * @param portfolioId Portfolio ID
     * @param limit Maximum number of results
     * @return Top N assets sorted by weight descending
     */
    List<PortfolioListResponse.TopAsset> findTopAssetsByWeight(UUID portfolioId, int limit);
}