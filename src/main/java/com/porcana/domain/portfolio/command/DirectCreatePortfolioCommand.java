package com.porcana.domain.portfolio.command;

import com.porcana.domain.portfolio.dto.DirectCreatePortfolioRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DirectCreatePortfolioCommand {
    private final UUID userId;
    private final String name;
    private final List<AssetWeight> assets;

    @Getter
    @Builder
    public static class AssetWeight {
        private final UUID assetId;
        private final BigDecimal weightPct; // nullable - null이면 1/n 균등 배분
    }

    public static DirectCreatePortfolioCommand from(DirectCreatePortfolioRequest request, UUID userId) {
        List<AssetWeight> assets = request.assets().stream()
                .map(input -> AssetWeight.builder()
                        .assetId(input.assetId())
                        .weightPct(input.weightPct())
                        .build())
                .toList();

        return DirectCreatePortfolioCommand.builder()
                .userId(userId)
                .name(request.name())
                .assets(assets)
                .build();
    }
}
