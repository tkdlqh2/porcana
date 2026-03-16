package com.porcana.domain.portfolio.dto.baseline;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Holding Baseline 조회 응답
 */
@Getter
@Builder
public class BaselineResponse {
    private final boolean exists;
    private final UUID baselineId;
    private final UUID portfolioId;
    private final String sourceType;
    private final String baseCurrency;
    private final BigDecimal seedMoney;  // 설정한 시드 금액 (cashAmount와 투자금액 합)
    private final BigDecimal cashAmount;  // 잔여 현금
    private final LocalDateTime confirmedAt;
    private final List<ItemResponse> items;

    @Getter
    @Builder
    public static class ItemResponse {
        private final UUID assetId;
        private final String symbol;
        private final String name;
        private final String market;
        private final BigDecimal quantity;
        private final BigDecimal avgPrice;
        private final BigDecimal targetWeightPct;
        private final BigDecimal currentPrice;  // 현재가
        private final BigDecimal currentValue;  // 현재 평가금액 (quantity × currentPrice)
    }

    public static BaselineResponse notExists() {
        return BaselineResponse.builder()
                .exists(false)
                .build();
    }

    public static BaselineResponse from(PortfolioHoldingBaseline baseline, List<ItemResponse> items,
                                         BigDecimal seedMoney) {
        return BaselineResponse.builder()
                .exists(true)
                .baselineId(baseline.getId())
                .portfolioId(baseline.getPortfolioId())
                .sourceType(baseline.getSourceType().name())
                .baseCurrency(baseline.getBaseCurrency().name())
                .seedMoney(seedMoney)
                .cashAmount(baseline.getCashAmount())
                .confirmedAt(baseline.getConfirmedAt())
                .items(items)
                .build();
    }
}
