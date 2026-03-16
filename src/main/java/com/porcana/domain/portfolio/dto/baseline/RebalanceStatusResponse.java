package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 리밸런싱 상태 응답
 * 현재 보유 상태와 목표 비중의 괴리 확인
 */
@Getter
@Builder
public class RebalanceStatusResponse {
    private final UUID portfolioId;
    private final boolean hasBaseline;
    private final boolean needsRebalancing;
    private final LocalDateTime checkedAt;
    private final BigDecimal thresholdPct;  // 괴리 기준 (기본 5%)
    private final Summary summary;
    private final List<ItemStatus> items;

    @Getter
    @Builder
    public static class Summary {
        private final BigDecimal totalValueKrw;      // 전체 평가금액 (KRW)
        private final BigDecimal cashAmount;          // 현금 보유액
        private final BigDecimal maxDeviationPct;     // 최대 괴리도
    }

    @Getter
    @Builder
    public static class ItemStatus {
        private final UUID assetId;
        private final String symbol;
        private final String name;
        private final String market;
        private final BigDecimal targetWeightPct;     // 목표 비중
        private final BigDecimal currentWeightPct;    // 현재 비중
        private final BigDecimal deviationPct;        // 괴리도 (current - target)
        private final String action;                  // "BUY", "SELL", "HOLD"
        private final int currentQuantity;            // 현재 보유 수량
        private final BigDecimal currentPrice;        // 현재가
        private final BigDecimal currentValueKrw;     // 현재 평가금액 (KRW)
    }

    public static RebalanceStatusResponse noBaseline(UUID portfolioId) {
        return RebalanceStatusResponse.builder()
                .portfolioId(portfolioId)
                .hasBaseline(false)
                .needsRebalancing(false)
                .checkedAt(LocalDateTime.now())
                .build();
    }
}
