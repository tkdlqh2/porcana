package com.porcana.domain.arena.repository;

import com.porcana.domain.arena.entity.ArenaSession;
import com.porcana.domain.arena.entity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
