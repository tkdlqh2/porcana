package com.porcana.domain.portfolio.dto.deck;

import com.porcana.domain.asset.dto.personality.AssetPersonality;
import com.porcana.domain.asset.entity.Asset;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Asset + 비중 + Personality 조합 헬퍼
 */
@Getter
@AllArgsConstructor
public class PositionWithAsset {
    private final Asset asset;
    private final Double weightPct;
    private final AssetPersonality personality;
}
