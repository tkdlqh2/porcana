package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class StartPortfolioResponse {
    private final String portfolioId;
    private final String status;
    private final LocalDate startedAt;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @return StartPortfolioResponse
     */
    public static StartPortfolioResponse from(Portfolio portfolio) {
        return StartPortfolioResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .status(portfolio.getStatus().name())
                .startedAt(portfolio.getStartedAt())
                .build();
    }
}