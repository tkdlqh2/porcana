package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.PickPreferencesRequest;
import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class PickPreferencesCommand {
    private final RiskProfile riskProfile;
    private final List<Sector> sectors;

    public static PickPreferencesCommand from(PickPreferencesRequest request) {
        return PickPreferencesCommand.builder()
                .riskProfile(request.riskProfile())
                .sectors(new ArrayList<>(request.sectors()))
                .build();
    }
}