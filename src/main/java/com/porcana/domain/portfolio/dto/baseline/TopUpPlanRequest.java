package com.porcana.domain.portfolio.dto.baseline;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 추가 입금 추천 요청
 */
public record TopUpPlanRequest(
        @NotNull(message = "추가 금액은 필수입니다")
        @DecimalMin(value = "0", inclusive = false, message = "추가 금액은 0보다 커야 합니다")
        BigDecimal additionalCash
) {}
