package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>, AssetRepositoryCustom, JpaSpecificationExecutor<Asset> {

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
     * Count assets by IDs (for bulk existence check)
     */
    long countByIdIn(List<UUID> ids);

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
     * Find all active assets by market and type
     * Used for ETF-specific daily price updates
     */
    List<Asset> findByMarketAndTypeAndActiveTrue(Asset.Market market, Asset.AssetType type);

    /**
     * Find all assets by market and type (including inactive)
     * Used for bulk image updates
     */
    List<Asset> findByMarketAndType(Asset.Market market, Asset.AssetType type);

    /**
     * Find all assets by market and type with universe tags preloaded.
     * Used by batch runners that build prompts from tag metadata outside
     * of an open persistence context.
     */
    @Query("""
        SELECT DISTINCT a
        FROM Asset a
        LEFT JOIN FETCH a.universeTags
        WHERE a.market = :market
          AND a.type = :type
        """)
    List<Asset> findByMarketAndTypeWithTags(@Param("market") Asset.Market market,
                                            @Param("type") Asset.AssetType type);

    /**
     * Find inactive asset IDs by market and type.
     * Used by batch jobs that only need affected asset IDs.
     */
    @Query("SELECT a.id FROM Asset a WHERE a.market = :market AND a.type = :type AND a.active = false")
    List<UUID> findIdsByMarketAndTypeAndActiveFalse(@Param("market") Asset.Market market,
                                                    @Param("type") Asset.AssetType type);

    /**
     * Find all active assets
     * Used for risk calculation
     */
    List<Asset> findByActiveTrue();

    /**
     * Find all active assets in the specified sectors
     * Used for arena asset recommendations
     */
    List<Asset> findBySectorInAndActiveTrue(List<Sector> sectors);

    /**
     * Find all active assets NOT in the specified sectors
     * Used for wild card recommendations in arena
     */
    List<Asset> findBySectorNotInAndActiveTrue(List<Sector> sectors);

    /**
     * Find all active assets excluding specified IDs
     * Used for arena recommendations to avoid duplicates
     */
    List<Asset> findByActiveTrueAndIdNotIn(List<UUID> excludeIds);

    /**
     * Count active assets in a specific sector
     * Used to validate sector selection (ensure enough assets available)
     */
    Integer countBySectorAndActiveTrue(Sector sector);

    /**
     * Count active assets per sector
     * Used to reduce per-sector count queries in arena flows
     */
    @Query("SELECT a.sector AS sector, COUNT(a) AS count " +
           "FROM Asset a " +
           "WHERE a.active = true AND a.sector IS NOT NULL " +
           "GROUP BY a.sector")
    List<SectorCount> countActiveBySector();

    @Query("SELECT a.market AS market, COUNT(a) AS count " +
           "FROM Asset a " +
           "WHERE a.active = true " +
           "GROUP BY a.market")
    List<MarketCount> countActiveByMarket();

    @Query("SELECT a.type AS type, COUNT(a) AS count " +
           "FROM Asset a " +
           "WHERE a.active = true " +
           "GROUP BY a.type")
    List<AssetTypeCount> countActiveByType();

    interface SectorCount {
        Sector getSector();
        long getCount();
    }

    interface MarketCount {
        Asset.Market getMarket();
        long getCount();
    }

    interface AssetTypeCount {
        Asset.AssetType getType();
        long getCount();
    }

    // ========================================
    // Arena Bucket Sampling Queries
    // ========================================

    /**
     * Find min and max ID for random sampling
     */
    @Query("SELECT MIN(a.id) FROM Asset a WHERE a.active = true")
    Optional<UUID> findMinActiveId();

    @Query("SELECT MAX(a.id) FROM Asset a WHERE a.active = true")
    Optional<UUID> findMaxActiveId();

    /**
     * Sample preferred sector bucket (PK range random)
     * Used for normal picks in arena
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND a.sector = ANY(CAST(:sectors AS varchar[]))
          AND a.id >= :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findPreferredSectorBucket(
            @Param("sectors") String[] sectors,
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    /**
     * Sample preferred sector bucket (wrap-around fallback)
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND a.sector = ANY(CAST(:sectors AS varchar[]))
          AND a.id < :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findPreferredSectorBucketWrapAround(
            @Param("sectors") String[] sectors,
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    /**
     * Sample non-preferred sector bucket (PK range random)
     * Used for diversity in normal picks
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND NOT (a.sector = ANY(CAST(:sectors AS varchar[])))
          AND a.id >= :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findNonPreferredSectorBucket(
            @Param("sectors") String[] sectors,
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    /**
     * Sample non-preferred sector bucket (wrap-around fallback)
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND NOT (a.sector = ANY(CAST(:sectors AS varchar[])))
          AND a.id < :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findNonPreferredSectorBucketWrapAround(
            @Param("sectors") String[] sectors,
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    /**
     * Sample wild bucket (PK range random)
     * Used for wild picks - no sector filter
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND a.id >= :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findWildBucket(
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    /**
     * Sample wild bucket (wrap-around fallback)
     */
    @Query(value = """
        SELECT * FROM assets a
        WHERE a.active = true
          AND a.id < :randId
          AND NOT (a.id = ANY(:excludeIds))
        ORDER BY a.id
        LIMIT :limit
        """, nativeQuery = true)
    List<Asset> findWildBucketWrapAround(
            @Param("randId") UUID randId,
            @Param("excludeIds") UUID[] excludeIds,
            @Param("limit") int limit
    );

    // ========================================
    // Admin API Support
    // ========================================

    /**
     * Find all assets with pagination for admin
     */
    Page<Asset> findAll(Pageable pageable);

    /**
     * Find assets by market with pagination for admin
     */
    Page<Asset> findByMarket(Asset.Market market, Pageable pageable);
}
