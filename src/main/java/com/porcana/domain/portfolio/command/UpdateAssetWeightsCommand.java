package com.porcana.domain.portfolio.command;

import com.porcana.domain.portfolio.dto.UpdateAssetWeightsRequest;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class UpdateAssetWeightsCommand {
    private final UUID portfolioId;
    private final UUID userId;
    private final List<AssetWeightUpdate> weights;

    @Getter
    @Builder
    public static class AssetWeightUpdate {
        private final UUID assetId;
        private final BigDecimal weightPct;
    }

    public static UpdateAssetWeightsCommand from(UpdateAssetWeightsRequest request, UUID portfolioId, UUID userId) {
        List<AssetWeightUpdate> weights = request.weights().stream()
                .map(w -> AssetWeightUpdate.builder()
                        .assetId(UUID.fromString(w.assetId()))
                        .weightPct(BigDecimal.valueOf(w.weightPct()))
                        .build())
                .collect(Collectors.toList());

        return UpdateAssetWeightsCommand.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .weights(weights)
                .build();
    }
}