package com.porcana.domain.portfolio.dto;

import com.porcana.domain.portfolio.entity.Portfolio;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class CreatePortfolioResponse {
    private final String portfolioId;
    private final String name;
    private final String status;
    private final LocalDate createdAt;

    /**
     * Factory method to create response from Portfolio entity
     *
     * @param portfolio Portfolio entity
     * @return CreatePortfolioResponse
     */
    public static CreatePortfolioResponse from(Portfolio portfolio) {
        return CreatePortfolioResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .createdAt(portfolio.getCreatedAt().toLocalDate())
                .build();
    }
}