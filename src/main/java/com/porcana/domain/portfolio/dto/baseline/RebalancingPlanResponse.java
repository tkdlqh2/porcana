package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 리밸런싱 플랜 응답
 * BUY + SELL 모두 포함
 */
@Getter
@Builder
public class RebalancingPlanResponse {
    private final UUID portfolioId;
    private final UUID baselineId;
    private final boolean needsRebalancing;
    private final BigDecimal thresholdPct;
    private final Summary summary;
    private final List<ActionItem> actions;

    @Getter
    @Builder
    public static class Summary {
        private final BigDecimal totalValueKrw;       // 현재 총 평가금액
        private final BigDecimal totalBuyAmount;      // 총 매수 금액
        private final BigDecimal totalSellAmount;     // 총 매도 금액
        private final BigDecimal netCashFlow;         // 순 현금 흐름 (sell - buy)
        private final BigDecimal cashAfterRebalance;  // 리밸런싱 후 예상 현금
    }

    @Getter
    @Builder
    public static class ActionItem {
        private final UUID assetId;
        private final String symbol;
        private final String name;
        private final String market;
        private final String action;                  // "BUY" or "SELL"
        private final BigDecimal targetWeightPct;     // 목표 비중
        private final BigDecimal currentWeightPct;    // 현재 비중
        private final BigDecimal deviationPct;        // 괴리도
        private final int currentQuantity;            // 현재 수량
        private final int actionQuantity;             // 매수/매도 수량
        private final int afterQuantity;              // 실행 후 수량
        private final BigDecimal currentPrice;        // 현재가
        private final BigDecimal actionAmountKrw;     // 매수/매도 금액 (KRW)
    }

    public static RebalancingPlanResponse noBaseline(UUID portfolioId) {
        return RebalancingPlanResponse.builder()
                .portfolioId(portfolioId)
                .needsRebalancing(false)
                .build();
    }

    public static RebalancingPlanResponse noRebalancingNeeded(UUID portfolioId, UUID baselineId,
                                                               BigDecimal thresholdPct, BigDecimal totalValueKrw) {
        return RebalancingPlanResponse.builder()
                .portfolioId(portfolioId)
                .baselineId(baselineId)
                .needsRebalancing(false)
                .thresholdPct(thresholdPct)
                .summary(Summary.builder()
                        .totalValueKrw(totalValueKrw)
                        .totalBuyAmount(BigDecimal.ZERO)
                        .totalSellAmount(BigDecimal.ZERO)
                        .netCashFlow(BigDecimal.ZERO)
                        .build())
                .actions(List.of())
                .build();
    }
}
