package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 리밸런싱 플랜 응답
 * BUY + SELL 모두 포함
 */
@Builder
public record RebalancingPlanResponse(
        UUID portfolioId,
        UUID baselineId,
        boolean needsRebalancing,
        BigDecimal thresholdPct,
        Summary summary,
        List<ActionItem> actions
) {
    @Builder
    public record Summary(
            BigDecimal totalValueKrw,        // 현재 총 평가금액
            BigDecimal totalBuyAmount,       // 총 매수 금액
            BigDecimal totalSellAmount,      // 총 매도 금액
            BigDecimal netCashFlow,          // 순 현금 흐름 (sell - buy)
            BigDecimal cashAfterRebalance    // 리밸런싱 후 예상 현금
    ) {}

    @Builder
    public record ActionItem(
            UUID assetId,
            String symbol,
            String name,
            String market,
            String action,                   // "BUY" or "SELL"
            BigDecimal targetWeightPct,      // 목표 비중
            BigDecimal currentWeightPct,     // 현재 비중
            BigDecimal deviationPct,         // 괴리도
            int currentQuantity,             // 현재 수량
            int actionQuantity,              // 매수/매도 수량
            int afterQuantity,               // 실행 후 수량
            BigDecimal currentPrice,         // 현재가
            BigDecimal actionAmountKrw       // 매수/매도 금액 (KRW)
    ) {}

    public static RebalancingPlanResponse noBaseline(UUID portfolioId) {
        return new RebalancingPlanResponse(
                portfolioId,
                null,
                false,
                null,
                null,
                Collections.emptyList()
        );
    }

    public static RebalancingPlanResponse noRebalancingNeeded(UUID portfolioId, UUID baselineId,
                                                               BigDecimal thresholdPct, BigDecimal totalValueKrw) {
        return new RebalancingPlanResponse(
                portfolioId,
                baselineId,
                false,
                thresholdPct,
                new Summary(
                        totalValueKrw,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null
                ),
                List.of()
        );
    }
}