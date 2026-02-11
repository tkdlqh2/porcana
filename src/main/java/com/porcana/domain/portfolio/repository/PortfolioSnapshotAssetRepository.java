package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioSnapshotAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioSnapshotAssetRepository extends JpaRepository<PortfolioSnapshotAsset, UUID> {

    /**
     * Find all assets in a snapshot
     */
    List<PortfolioSnapshotAsset> findBySnapshotId(UUID snapshotId);

    /**
     * Find specific asset in a snapshot
     */
    Optional<PortfolioSnapshotAsset> findBySnapshotIdAndAssetId(UUID snapshotId, UUID assetId);

    /**
     * Delete all assets in a snapshot
     * Returns the number of deleted assets
     */
    int deleteBySnapshotId(UUID snapshotId);

    /**
     * Check if asset exists in snapshot
     */
    boolean existsBySnapshotIdAndAssetId(UUID snapshotId, UUID assetId);

    /**
     * Count assets in a snapshot
     */
    long countBySnapshotId(UUID snapshotId);
}