package com.porcana.domain.portfolio.dto.baseline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 추가 입금 실행 요청
 * 실제 매수한 내역을 반영하여 baseline 업데이트
 */
public record ExecuteTopUpRequest(
        @NotNull(message = "추가 입금액은 필수입니다")
        @DecimalMin(value = "0", inclusive = false, message = "추가 입금액은 0보다 커야 합니다")
        BigDecimal additionalCash,

        @NotEmpty(message = "매수 내역은 필수입니다")
        @Valid
        List<PurchaseItem> purchases,

        /**
         * 남은 현금을 baseline에 추가할지 여부
         * true: cashAmount에 remainingCash 추가
         * false: 남은 현금 무시 (기본값)
         */
        Boolean addRemainingCashToBaseline
) {
    public record PurchaseItem(
            @NotNull(message = "자산 ID는 필수입니다")
            UUID assetId,

            @NotNull(message = "매수 수량은 필수입니다")
            @DecimalMin(value = "0", inclusive = false, message = "매수 수량은 0보다 커야 합니다")
            BigDecimal quantity,

            @NotNull(message = "매수 단가는 필수입니다")
            @DecimalMin(value = "0", inclusive = false, message = "매수 단가는 0보다 커야 합니다")
            BigDecimal purchasePrice
    ) {}
}