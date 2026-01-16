package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RoundType;

import java.util.UUID;

/**
 * Common interface for all round response types
 */
public interface RoundResponse {
    UUID getSessionId();
    Integer getRound();
    RoundType getRoundType();
}
