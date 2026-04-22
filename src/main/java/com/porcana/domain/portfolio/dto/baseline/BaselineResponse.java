package com.porcana.domain.portfolio.dto.baseline;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Holding Baseline 조회 응답
 */
@Builder
public record BaselineResponse(
        boolean exists,
        UUID baselineId,
        UUID portfolioId,
        String sourceType,
        String baseCurrency,
        BigDecimal seedMoney,      // 원본 시드 금액 (설정 시점의 투자 원금)
        BigDecimal totalValue,     // 현재 시가평가 총액 (현재가 기준)
        BigDecimal cashAmount,     // 잔여 현금
        LocalDateTime confirmedAt,
        List<ItemResponse> items
) {
    @Builder
    public record ItemResponse(
            UUID assetId,
            String symbol,
            String name,
            String imageUrl,
            String market,
            BigDecimal quantity,
            BigDecimal avgPrice,
            BigDecimal targetWeightPct,
            BigDecimal currentPrice,   // 현재가
            BigDecimal currentValue    // 현재 평가금액 (quantity × currentPrice)
    ) {}

    public static BaselineResponse notExists() {
        return new BaselineResponse(false, null, null, null, null, null, null, null, null, null);
    }

    public static BaselineResponse from(PortfolioHoldingBaseline baseline, List<ItemResponse> items,
                                         BigDecimal seedMoney, BigDecimal totalValue) {
        return new BaselineResponse(
                true,
                baseline.getId(),
                baseline.getPortfolioId(),
                baseline.getSourceType().name(),
                baseline.getBaseCurrency().name(),
                seedMoney,
                totalValue,
                baseline.getCashAmount(),
                baseline.getConfirmedAt(),
                items
        );
    }
}
