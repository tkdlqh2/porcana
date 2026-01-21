package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Search active assets by symbol or name (case-insensitive)
     * Used for asset search API
     */
    @Query("SELECT a FROM Asset a WHERE a.active = true " +
           "AND (LOWER(a.symbol) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY " +
           "CASE WHEN LOWER(a.symbol) = LOWER(:query) THEN 0 " +
           "     WHEN LOWER(a.symbol) LIKE LOWER(CONCAT(:query, '%')) THEN 1 " +
           "     WHEN LOWER(a.name) LIKE LOWER(CONCAT(:query, '%')) THEN 2 " +
           "     ELSE 3 END, " +
           "a.symbol")
    List<Asset> searchBySymbolOrName(@Param("query") String query, org.springframework.data.domain.Pageable pageable);

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
}