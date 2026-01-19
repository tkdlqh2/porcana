package com.porcana.domain.portfolio.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PortfolioListResponse {
    private final String portfolioId;
    private final String name;
    private final String status;
    private final Boolean isMain;
    private final Double totalReturnPct;
    private final LocalDate createdAt;
}