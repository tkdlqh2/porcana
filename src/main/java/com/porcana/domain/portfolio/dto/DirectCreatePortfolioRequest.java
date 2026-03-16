package com.porcana.domain.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 직접 종목/비중을 입력하여 포트폴리오 생성하는 요청
 * 아레나 방식이 아닌 직접 생성 방식
 */
public record DirectCreatePortfolioRequest(
        @NotBlank(message = "포트폴리오 이름은 필수입니다")
        String name,

        @NotNull(message = "종목 목록은 필수입니다.")
        @Size(min = 5, max = 20, message = "종목 개수는 5~20개 사이여야 합니다")
        @Valid
        List<AssetInput> assets
) {
    public record AssetInput(
            @NotNull(message = "자산 ID는 필수입니다")
            UUID assetId,
            /**
             * 비중 (optional)
             * - null이면 1/n 균등 배분
             * - 값이 있으면 해당 비중 적용 (전체 합 100% 필수)
             */
            BigDecimal weightPct
    ) {}
}
