package com.porcana.domain.arena.command;

import com.porcana.domain.arena.dto.PickPreferencesRequest;
import com.porcana.domain.arena.entity.RiskProfile;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class PickPreferencesCommand {
    private final RiskProfile riskProfile;
    private final List<Sector> sectors;
    private final List<Asset.Market> markets;
    private final List<Asset.AssetType> assetTypes;

    public static PickPreferencesCommand from(PickPreferencesRequest request) {
        return PickPreferencesCommand.builder()
                .riskProfile(request.riskProfile())
                .sectors(request.sectors() == null ? Collections.emptyList() : List.copyOf(request.sectors()))
                .markets(request.markets() == null ? Collections.emptyList() : List.copyOf(request.markets()))
                .assetTypes(request.assetTypes() == null ? Collections.emptyList() : List.copyOf(request.assetTypes()))
                .build();
    }
}
