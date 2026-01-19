package com.porcana.domain.home.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MainPortfolioIdResponse {
    private final UUID mainPortfolioId;
}