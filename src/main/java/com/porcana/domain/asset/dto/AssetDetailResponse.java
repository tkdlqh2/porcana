package com.porcana.domain.asset.dto;

import com.porcana.domain.asset.dto.personality.AssetPersonalityResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "자산 상세 정보")
public class AssetDetailResponse {

    @Schema(description = "자산 ID")
    private final String assetId;

    @Schema(description = "티커", example = "AAPL")
    private final String ticker;

    @Schema(description = "종목명", example = "Apple Inc.")
    private final String name;

    @Schema(description = "거래소", example = "US")
    private final String exchange;

    @Schema(description = "국가", example = "US")
    private final String country;

    @Schema(description = "섹터", example = "INFORMATION_TECHNOLOGY")
    private final String sector;

    @Schema(description = "통화", example = "USD")
    private final String currency;

    @Schema(description = "이미지 URL")
    private final String imageUrl;

    @Schema(description = "설명")
    private final String description;

    @Schema(description = "현재 위험도 (1-5)", example = "3")
    private final Integer currentRiskLevel;

    @Schema(description = "자산 성격 정보")
    private final AssetPersonalityResponse personality;
}