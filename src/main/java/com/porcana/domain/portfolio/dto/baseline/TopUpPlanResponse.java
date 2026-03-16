package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 추가 입금 추천 응답
 * BUY only - 매도 없이 부족한 자산만 채움
 */
@Getter
@Builder
public class TopUpPlanResponse {
    private final UUID portfolioId;
    private final BigDecimal additionalCash;
    private final String baseCurrency;
    private final BigDecimal currentTotalValue;  // 현재 총 평가금액
    private final BigDecimal newTotalValue;      // 추가 후 총 금액
    private final List<RecommendationItem> recommendations;
    private final BigDecimal remainingCash;      // 사용 후 남는 현금

    @Getter
    @Builder
    public static class RecommendationItem {
        private final UUID assetId;
        private final String symbol;
        private final String name;
        private final String market;
        private final BigDecimal targetWeightPct;     // 목표 비중
        private final BigDecimal currentWeightPct;    // 현재 비중
        private final BigDecimal weightAfterBuy;      // 매수 후 예상 비중
        private final BigDecimal currentPrice;        // 현재가
        private final int recommendedQuantity;        // 추천 매수 수량
        private final BigDecimal recommendedAmount;   // 추천 매수 금액
        private final String reason;                  // 추천 이유
    }
}
