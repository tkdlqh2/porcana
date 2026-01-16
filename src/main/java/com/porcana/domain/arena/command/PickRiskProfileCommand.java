package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.PickRiskProfileRequest;
import com.porcana.domain.arena.entity.RiskProfile;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PickRiskProfileCommand {
    private final RiskProfile riskProfile;

    public static PickRiskProfileCommand from(PickRiskProfileRequest request) {
        return PickRiskProfileCommand.builder()
                .riskProfile(request.riskProfile())
                .build();
    }
}
