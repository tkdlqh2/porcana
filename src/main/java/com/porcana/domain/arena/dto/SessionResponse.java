package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.entity.SessionStatus;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SessionResponse {
    private UUID sessionId;
    private UUID portfolioId;
    private SessionStatus status;
    private Integer currentRound;
    private Integer totalRounds;
    private RiskProfile riskProfile;
    private List<Sector> selectedSectors;
    private List<UUID> selectedAssetIds;
}
