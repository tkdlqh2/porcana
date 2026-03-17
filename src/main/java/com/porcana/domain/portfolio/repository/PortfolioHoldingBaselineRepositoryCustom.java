package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;

import java.util.Optional;
import java.util.UUID;

/**
 * PortfolioHoldingBaseline Repository Custom Interface for QueryDSL
 */
public interface PortfolioHoldingBaselineRepositoryCustom {

    /**
     * Holding Baseline과 Items를 함께 조회 (N+1 방지)
     */
    Optional<PortfolioHoldingBaseline> findByPortfolioIdWithItems(UUID portfolioId);
}