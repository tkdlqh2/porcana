package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetPriceRepository extends JpaRepository<AssetPrice, UUID> {

    /**
     * Find price data for a specific asset and date
     */
    Optional<AssetPrice> findByAssetAndPriceDate(Asset asset, LocalDate priceDate);

    /**
     * Find all price data for an asset within date range
     */
    List<AssetPrice> findByAssetAndPriceDateBetweenOrderByPriceDateAsc(
            Asset asset, LocalDate startDate, LocalDate endDate);

    /**
     * Find latest price data for an asset
     */
    Optional<AssetPrice> findFirstByAssetOrderByPriceDateDesc(Asset asset);

    /**
     * Check if price data exists for asset and date
     */
    boolean existsByAssetAndPriceDate(Asset asset, LocalDate priceDate);

    /**
     * Delete price data older than a specific date
     */
    @Modifying
    void deleteByPriceDateBefore(LocalDate date);

    /**
     * Delete price data from a specific date onwards
     */
    @Modifying
    void deleteByPriceDateGreaterThanEqual(LocalDate date);

    /**
     * Check if any price data exists for an asset
     * Used to avoid re-fetching historical data
     */
    boolean existsByAsset(Asset asset);

    /**
     * Find recent N price data for an asset (ordered by date ascending)
     * Used for risk calculation
     */
    List<AssetPrice> findTop252ByAssetOrderByPriceDateDesc(Asset asset);

    /**
     * Find recent price data for an asset by asset ID
     */
    List<AssetPrice> findByAssetIdOrderByPriceDateAsc(UUID assetId);
}