package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdatePortfolioNameResponse {
    private final String portfolioId;
    private final String name;
}