package com.porcana.domain.portfolio.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePortfolioRequest(
        @NotBlank(message = "포트폴리오 이름은 필수입니다")
        String name
) {
}