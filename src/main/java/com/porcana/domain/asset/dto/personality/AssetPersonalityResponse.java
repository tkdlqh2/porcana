package com.porcana.domain.asset.dto.personality;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 자산 성격 API 응답 DTO
 */
@Getter
@Builder
@Schema(description = "자산 성격 정보")
public class AssetPersonalityResponse {

    @Schema(description = "역할", example = "CORE")
    private final String role;

    @Schema(description = "역할 한글명", example = "핵심")
    private final String roleDisplayName;

    @Schema(description = "역할 설명", example = "포트폴리오의 안정적 기반이 되는 종목")
    private final String roleDescription;

    @Schema(description = "위험도 (1-5)", example = "3")
    private final Integer riskLevel;

    @Schema(description = "노출 유형", example = "SINGLE_STOCK")
    private final String exposureType;

    @Schema(description = "노출 유형 한글명", example = "개별 주식")
    private final String exposureTypeDisplayName;

    @Schema(description = "페르소나", example = "GROWTH")
    private final String persona;

    @Schema(description = "페르소나 한글명", example = "성장형")
    private final String personaDisplayName;

    @Schema(description = "배당 프로필", example = "HAS_DIVIDEND")
    private final String dividendProfile;

    @Schema(description = "배당 프로필 한글명", example = "배당 있음")
    private final String dividendProfileDisplayName;

    /**
     * AssetPersonality VO로부터 Response 생성
     */
    public static AssetPersonalityResponse from(AssetPersonality personality) {
        return AssetPersonalityResponse.builder()
                .role(personality.getRole().name())
                .roleDisplayName(personality.getRole().getDisplayName())
                .roleDescription(personality.getRole().getDescription())
                .riskLevel(personality.getRiskLevel())
                .exposureType(personality.getExposureType().name())
                .exposureTypeDisplayName(personality.getExposureType().getDisplayName())
                .persona(personality.getPersona().name())
                .personaDisplayName(personality.getPersona().getDisplayName())
                .dividendProfile(personality.getDividendProfile().name())
                .dividendProfileDisplayName(personality.getDividendProfile().getDisplayName())
                .build();
    }
}
