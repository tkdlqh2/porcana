package com.porcana.domain.portfolio.dto.baseline;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 추가 입금 실행 결과
 */
@Builder
public record ExecuteTopUpResponse(
        UUID portfolioId,
        UUID baselineId,
        String baseCurrency,
        Summary summary,
        List<UpdatedItem> updatedItems
) {
    @Builder
    public record Summary(
            BigDecimal additionalCash,       // 추가 입금액
            BigDecimal totalPurchaseAmount,  // 총 매수 금액
            BigDecimal remainingCash,        // 남은 현금
            BigDecimal previousTotalValue,   // 이전 총 평가금액
            BigDecimal newTotalValue,        // 새 총 평가금액
            BigDecimal newCashAmount         // 업데이트된 현금 보유액
    ) {}

    @Builder
    public record UpdatedItem(
            UUID assetId,
            String symbol,
            String name,
            BigDecimal previousQuantity,     // 이전 수량
            BigDecimal addedQuantity,        // 추가 수량
            BigDecimal newQuantity,          // 새 수량
            BigDecimal previousAvgPrice,     // 이전 평균 단가
            BigDecimal newAvgPrice           // 새 평균 단가 (가중 평균)
    ) {}
}