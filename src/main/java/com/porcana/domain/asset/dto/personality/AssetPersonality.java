package com.porcana.domain.asset.dto.personality;

import com.porcana.domain.asset.entity.personality.*;
import lombok.Builder;
import lombok.Getter;

/**
 * 자산 성격 내부 Value Object
 */
@Getter
@Builder
public class AssetPersonality {
    private final Role role;
    private final Integer riskLevel;
    private final ExposureType exposureType;
    private final Persona persona;
    private final DividendProfile dividendProfile;
}
