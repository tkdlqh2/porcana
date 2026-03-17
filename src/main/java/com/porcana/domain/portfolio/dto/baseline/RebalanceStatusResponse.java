package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 리밸런싱 상태 응답
 * 현재 보유 상태와 목표 비중의 괴리 확인
 */
@Builder
public record RebalanceStatusResponse(
        UUID portfolioId,
        boolean hasBaseline,
        boolean needsRebalancing,
        LocalDateTime checkedAt,
        BigDecimal thresholdPct,   // 괴리 기준 (기본 5%)
        Summary summary,
        List<ItemStatus> items
) {
    @Builder
    public record Summary(
            BigDecimal totalValueKrw,       // 전체 평가금액 (KRW)
            BigDecimal cashAmount,          // 현금 보유액
            BigDecimal maxDeviationPct      // 최대 괴리도
    ) {}

    @Builder
    public record ItemStatus(
            UUID assetId,
            String symbol,
            String name,
            String market,
            BigDecimal targetWeightPct,      // 목표 비중
            BigDecimal currentWeightPct,     // 현재 비중
            BigDecimal deviationPct,         // 괴리도 (current - target)
            String action,                   // "BUY", "SELL", "HOLD"
            int currentQuantity,             // 현재 보유 수량
            BigDecimal currentPrice,         // 현재가
            BigDecimal currentValueKrw       // 현재 평가금액 (KRW)
    ) {}

    public static RebalanceStatusResponse noBaseline(UUID portfolioId) {
        return new RebalanceStatusResponse(
                portfolioId,
                false,
                false,
                LocalDateTime.now(),
                null,
                null,
                null
        );
    }
}