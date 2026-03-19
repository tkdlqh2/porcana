package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 추가 입금 추천 응답
 * BUY only - 매도 없이 부족한 자산만 채움
 */
@Builder
public record TopUpPlanResponse(
        UUID portfolioId,
        BigDecimal additionalCash,
        String baseCurrency,
        BigDecimal currentTotalValue,   // 현재 총 평가금액
        BigDecimal newTotalValue,       // 추가 후 총 금액
        List<RecommendationItem> recommendations,
        BigDecimal remainingCash        // 사용 후 남는 현금
) {
    @Builder
    public record RecommendationItem(
            UUID assetId,
            String symbol,
            String name,
            String market,
            BigDecimal targetWeightPct,      // 목표 비중
            BigDecimal currentWeightPct,     // 현재 비중
            BigDecimal weightAfterBuy,       // 매수 후 예상 비중
            BigDecimal currentPrice,         // 현재가
            int recommendedQuantity,         // 추천 매수 수량
            BigDecimal recommendedAmount,    // 추천 매수 금액
            String reason                    // 추천 이유
    ) {}
}