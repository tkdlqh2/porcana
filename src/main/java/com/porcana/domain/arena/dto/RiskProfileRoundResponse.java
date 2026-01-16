package com.porcana.domain.arena.dto;

import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.arena.entity.RoundType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RiskProfileRoundResponse implements RoundResponse {
    private UUID sessionId;
    private Integer round;
    private RoundType roundType;
    private List<RiskProfileOption> options;

    @Getter
    @Builder
    public static class RiskProfileOption {
        private RiskProfile value;
        private String displayName;
        private String description;
    }
}
