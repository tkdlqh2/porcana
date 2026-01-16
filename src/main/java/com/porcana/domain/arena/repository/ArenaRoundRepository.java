package com.porcana.domain.arena.repository;

import com.porcana.domain.arena.entity.ArenaRound;
import com.porcana.domain.arena.entity.RoundType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArenaRoundRepository extends JpaRepository<ArenaRound, UUID> {

    /**
     * Find round by session ID and round number
     * Used for idempotent round retrieval
     */
    Optional<ArenaRound> findBySessionIdAndRoundNumber(UUID sessionId, Integer roundNumber);

    /**
     * Find all rounds for a session, ordered by round number
     * Used for session summary
     */
    List<ArenaRound> findBySessionIdOrderByRoundNumberAsc(UUID sessionId);

    /**
     * Find rounds by session ID and round type
     * Used to get all ASSET rounds for portfolio completion
     */
    List<ArenaRound> findBySessionIdAndRoundType(UUID sessionId, RoundType roundType);
}
