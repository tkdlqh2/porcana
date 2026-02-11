package com.porcana.domain.arena.repository;

import com.porcana.domain.arena.entity.ArenaSession;
import com.porcana.domain.arena.entity.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArenaSessionRepository extends JpaRepository<ArenaSession, UUID> {

    /**
     * Find session by portfolio ID and status
     * Used to check for existing in-progress sessions
     */
    Optional<ArenaSession> findByPortfolioIdAndStatus(UUID portfolioId, SessionStatus status);

    /**
     * Find all sessions for a user, ordered by creation date
     * Used for user's session history
     */
    List<ArenaSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find session by ID and user ID
     * Used for ownership validation
     */
    Optional<ArenaSession> findByIdAndUserId(UUID id, UUID userId);

    // ===== Guest Session Support =====

    /**
     * Find all sessions for a guest session, ordered by creation date
     */
    List<ArenaSession> findByGuestSessionIdOrderByCreatedAtDesc(UUID guestSessionId);

    /**
     * Find session by ID and guest session ID
     * Used for ownership validation
     */
    Optional<ArenaSession> findByIdAndGuestSessionId(UUID id, UUID guestSessionId);

    /**
     * Find arena sessions by guest session ID with pessimistic lock (for claim operation)
     * Used to prevent concurrent claim of the same guest sessions
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ArenaSession a WHERE a.guestSessionId = :guestSessionId")
    List<ArenaSession> findByGuestSessionIdForUpdate(@Param("guestSessionId") UUID guestSessionId);

    // ===== Portfolio Cleanup Support =====

    /**
     * Find all sessions for a portfolio
     * Used for hard-deleting portfolios
     */
    List<ArenaSession> findByPortfolioId(UUID portfolioId);

    /**
     * Delete all sessions for a portfolio
     * Returns the number of deleted sessions
     */
    int deleteByPortfolioId(UUID portfolioId);
}
