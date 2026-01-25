package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdatePortfolioNameResponse {
    private final String portfolioId;
    private final String name;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @return UpdatePortfolioNameResponse
     */
    public static UpdatePortfolioNameResponse from(Portfolio portfolio) {
        return UpdatePortfolioNameResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .build();
    }
}