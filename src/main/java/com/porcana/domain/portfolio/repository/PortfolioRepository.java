package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID>, PortfolioRepositoryCustom {

    /**
     * Find all portfolios for a user (excluding deleted)
     */
    List<Portfolio> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /**
     * Find active and finished portfolios for a user (exclude DRAFT and deleted)
     */
    List<Portfolio> findByUserIdAndStatusNotAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, PortfolioStatus status);

    /**
     * Find portfolio by ID and user ID (ownership validation, excluding deleted)
     */
    Optional<Portfolio> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    /**
     * Find portfolios by user and status (excluding deleted)
     */
    List<Portfolio> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, PortfolioStatus status);

    /**
     * Find portfolios by status (for batch processing, excluding deleted)
     */
    List<Portfolio> findByStatusAndDeletedAtIsNull(PortfolioStatus status);

    /**
     * Find portfolios by status with pagination (for chunk-based batch processing, excluding deleted)
     */
    Page<Portfolio> findByStatusAndDeletedAtIsNull(PortfolioStatus status, Pageable pageable);

    // ===== Guest Session Support =====

    /**
     * Find all portfolios for a guest session (excluding deleted)
     */
    List<Portfolio> findByGuestSessionIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID guestSessionId);

    /**
     * Find active and finished portfolios for a guest session (exclude DRAFT and deleted)
     */
    List<Portfolio> findByGuestSessionIdAndStatusNotAndDeletedAtIsNullOrderByCreatedAtDesc(UUID guestSessionId, PortfolioStatus status);

    /**
     * Find portfolio by ID and guest session ID (ownership validation, excluding deleted)
     */
    Optional<Portfolio> findByIdAndGuestSessionIdAndDeletedAtIsNull(UUID id, UUID guestSessionId);

    /**
     * Find portfolios by guest session ID with pessimistic lock (for claim operation, excluding deleted)
     * Used to prevent concurrent claim of the same guest portfolios
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.guestSessionId = :guestSessionId AND p.deletedAt IS NULL")
    List<Portfolio> findByGuestSessionIdForUpdate(@Param("guestSessionId") UUID guestSessionId);

    /**
     * Count portfolios owned by a guest session (excluding deleted)
     */
    long countByGuestSessionIdAndDeletedAtIsNull(UUID guestSessionId);

    // ===== Soft Delete Support =====

    /**
     * Find portfolio by ID (excluding deleted)
     */
    Optional<Portfolio> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Find deleted portfolios older than the specified date (for hard delete batch job)
     */
    @Query("SELECT p FROM Portfolio p WHERE p.deletedAt IS NOT NULL AND p.deletedAt < :cutoffDate")
    List<Portfolio> findDeletedPortfoliosOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find portfolios by ID list filtered by status (excluding deleted).
     * Used by the asset status check job to finish portfolios containing deactivated assets.
     */
    @Query("SELECT p FROM Portfolio p WHERE p.id IN :ids AND p.status = :status AND p.deletedAt IS NULL")
    List<Portfolio> findActiveByIdIn(@Param("ids") List<UUID> ids, @Param("status") PortfolioStatus status);

    // ===== Admin API Support =====

    /**
     * Find all portfolios with pagination (excluding deleted)
     * Used for admin portfolio list view
     */
    Page<Portfolio> findByDeletedAtIsNull(Pageable pageable);

    /**
     * Find all portfolios with pagination (including deleted) for admin
     */
    Page<Portfolio> findAll(Pageable pageable);

    /**
     * Count portfolios for a user (excluding deleted)
     * Used for admin user detail view
     */
    long countByUserIdAndDeletedAtIsNull(UUID userId);
}
