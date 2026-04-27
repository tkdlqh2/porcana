package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioHoldingBaselineRepository extends JpaRepository<PortfolioHoldingBaseline, UUID>,
        PortfolioHoldingBaselineRepositoryCustom {

    /**
     * Find the holding baseline for a portfolio.
     * Each portfolio can have at most one baseline row due to the unique index.
     */
    Optional<PortfolioHoldingBaseline> findByPortfolioId(UUID portfolioId);

    /**
     * Check whether a portfolio has a holding baseline.
     */
    boolean existsByPortfolioId(UUID portfolioId);

    /**
     * Fetch portfolio IDs that already have a holding baseline.
     * Used by portfolio list APIs to avoid N+1 existence checks.
     */
    @Query("SELECT b.portfolioId FROM PortfolioHoldingBaseline b WHERE b.portfolioId IN :portfolioIds")
    List<UUID> findPortfolioIdsByPortfolioIdIn(@Param("portfolioIds") List<UUID> portfolioIds);

    /**
     * Delete the holding baseline for a portfolio.
     */
    void deleteByPortfolioId(UUID portfolioId);
}
