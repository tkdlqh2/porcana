package com.porcana.domain.portfolio.dto.baseline;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * 리밸런싱 플랜 요청
 */
public record RebalancingPlanRequest(
        @DecimalMin(value = "0.1", message = "임계값은 0.1% 이상이어야 합니다")
        @DecimalMax(value = "50.0", message = "임계값은 50% 이하여야 합니다")
        BigDecimal thresholdPct  // 괴리 임계값 (기본 5%)
) {
    public RebalancingPlanRequest {
        if (thresholdPct == null) {
            thresholdPct = new BigDecimal("5.0");
        }
    }
}
