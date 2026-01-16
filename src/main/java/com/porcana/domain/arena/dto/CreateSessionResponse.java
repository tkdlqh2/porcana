package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.SessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateSessionResponse {
    private UUID sessionId;
    private UUID portfolioId;
    private SessionStatus status;
    private Integer currentRound;
}
