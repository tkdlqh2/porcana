package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
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

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @param isMain Whether this portfolio is the main portfolio
     * @param totalReturnPct Total return percentage
     * @return PortfolioListResponse
     */
    public static PortfolioListResponse from(Portfolio portfolio, boolean isMain, Double totalReturnPct) {
        return PortfolioListResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .isMain(isMain)
                .totalReturnPct(totalReturnPct)
                .createdAt(portfolio.getCreatedAt().toLocalDate())
                .build();
    }
}