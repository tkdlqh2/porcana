package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.SessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PickResponse {
    private UUID sessionId;
    private SessionStatus status;
    private Integer currentRound;
    private Object picked;  // Can be RiskProfile, List<Sector>, or UUID (assetId)
}
