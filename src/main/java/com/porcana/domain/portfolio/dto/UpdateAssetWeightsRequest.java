package com.porcana.domain.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateAssetWeightsRequest(
        @NotEmpty(message = "자산 비중 목록은 비어있을 수 없습니다")
        @Valid
        List<AssetWeight> weights,

        /**
         * true: baseline의 targetWeightPct도 함께 업데이트
         * false 또는 null: baseline 변경 없음 (기본값)
         */
        Boolean applyToBaseline
) {
    public record AssetWeight(
            @NotNull(message = "자산 ID는 필수입니다")
            String assetId,

            @NotNull(message = "비중은 필수입니다")
            Double weightPct
    ) {
    }
}