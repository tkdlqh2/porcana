package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}