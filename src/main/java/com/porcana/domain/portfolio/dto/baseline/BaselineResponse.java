package com.porcana.domain.portfolio.dto.baseline;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Holding Baseline 조회 응답
 */
public record BaselineResponse(
        boolean exists,
        UUID baselineId,
        UUID portfolioId,
        String sourceType,
        String baseCurrency,
        BigDecimal seedMoney,      // 설정한 시드 금액 (cashAmount와 투자금액 합)
        BigDecimal cashAmount,     // 잔여 현금
        LocalDateTime confirmedAt,
        List<ItemResponse> items
) {
    public record ItemResponse(
            UUID assetId,
            String symbol,
            String name,
            String market,
            BigDecimal quantity,
            BigDecimal avgPrice,
            BigDecimal targetWeightPct,
            BigDecimal currentPrice,   // 현재가
            BigDecimal currentValue    // 현재 평가금액 (quantity × currentPrice)
    ) {}

    public static BaselineResponse notExists() {
        return new BaselineResponse(false, null, null, null, null, null, null, null, null);
    }

    public static BaselineResponse from(PortfolioHoldingBaseline baseline, List<ItemResponse> items,
                                         BigDecimal seedMoney) {
        return new BaselineResponse(
                true,
                baseline.getId(),
                baseline.getPortfolioId(),
                baseline.getSourceType().name(),
                baseline.getBaseCurrency().name(),
                seedMoney,
                baseline.getCashAmount(),
                baseline.getConfirmedAt(),
                items
        );
    }
}