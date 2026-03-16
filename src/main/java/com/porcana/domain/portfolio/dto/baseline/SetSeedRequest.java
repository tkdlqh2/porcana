package com.porcana.domain.portfolio.dto.baseline;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 시드 금액 설정 요청
 * 포트폴리오의 비중과 현재가를 기반으로 각 종목별 수량을 자동 계산
 */
public record SetSeedRequest(
        @NotNull(message = "시드 금액은 필수입니다")
        @DecimalMin(value = "0", inclusive = false, message = "시드 금액은 0보다 커야 합니다")
        BigDecimal seedMoney,

        String baseCurrency  // 선택, 기본 KRW
) {}
