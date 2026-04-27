package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioAssetRepository extends JpaRepository<PortfolioAsset, UUID> {

    /**
     * Find all assets in a portfolio
     */
    List<PortfolioAsset> findByPortfolioId(UUID portfolioId);

    /**
     * Find asset in portfolio
     */
    Optional<PortfolioAsset> findByPortfolioIdAndAssetId(UUID portfolioId, UUID assetId);

    /**
     * Delete all assets in a portfolio
     * Returns the number of deleted assets
     */
    int deleteByPortfolioId(UUID portfolioId);

    /**
     * Find portfolio IDs that contain any of the given asset IDs.
     * Used by the asset status check job to find portfolios affected by deactivated assets.
     */
    @Query("SELECT DISTINCT pa.portfolioId FROM PortfolioAsset pa WHERE pa.assetId IN :assetIds")
    List<UUID> findPortfolioIdsByAssetIdIn(@Param("assetIds") List<UUID> assetIds);
}
