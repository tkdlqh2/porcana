package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /**
     * Find asset by symbol and market (natural key)
     * Used for upsert operations in batch jobs
     */
    Optional<Asset> findBySymbolAndMarket(String symbol, Asset.Market market);

    /**
     * Check if asset exists by symbol and market
     */
    boolean existsBySymbolAndMarket(String symbol, Asset.Market market);

    /**
     * Find assets by market that were created after a specific timestamp
     * Used to fetch recently created assets for historical price backfilling
     */
    List<Asset> findByMarketAndCreatedAtAfter(Asset.Market market, LocalDateTime createdAt);

    /**
     * Find all active assets by market
     * Used for daily price updates
     */
    List<Asset> findByMarketAndActiveTrue(Asset.Market market);

    /**
     * Find all active assets in the specified sectors
     * Used for arena asset recommendations
     */
    List<Asset> findBySectorInAndActiveTrue(List<Sector> sectors);

    /**
     * Count active assets in a specific sector
     * Used to validate sector selection (ensure enough assets available)
     */
    Integer countBySectorAndActiveTrue(Sector sector);
}