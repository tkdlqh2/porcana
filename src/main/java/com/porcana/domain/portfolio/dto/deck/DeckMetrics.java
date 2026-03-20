package com.porcana.domain.portfolio.dto.deck;

import com.porcana.domain.asset.entity.Sector;
import lombok.Builder;
import lombok.Getter;

/**
 * 덱 분석 지표 내부 Value Object
 */
@Getter
@Builder
public class DeckMetrics {
    private final Double weightedAverageRisk;
    private final Sector topSector;
    private final Double topSectorWeight;
    private final Double growthWeight;
    private final Double incomeWeight;
    private final Double defensiveWeight;
    private final Double hedgeWeight;
    private final Double coreWeight;
    private final Double etfWeight;
    private final Double stockWeight;
    private final Double usWeight;
    private final Double krWeight;
    // 배당 관련 지표
    private final Double hasDividendWeight;      // 배당이 있는 자산 비중 (HAS_DIVIDEND + DIVIDEND_FOCUSED + INCOME_CORE)
    private final Double dividendFocusedWeight;  // 배당 중심 자산 비중 (DIVIDEND_FOCUSED)
    private final Double incomeCoreWeight;       // 인컴 코어 자산 비중 (INCOME_CORE)
    private final Double top3Concentration;
    private final Integer assetCount;
}
