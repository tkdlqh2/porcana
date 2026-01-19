package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class StartPortfolioResponse {
    private final String portfolioId;
    private final String status;
    private final LocalDate startedAt;
}