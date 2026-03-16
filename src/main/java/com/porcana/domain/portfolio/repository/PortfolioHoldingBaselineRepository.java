package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioHoldingBaselineRepository extends JpaRepository<PortfolioHoldingBaseline, UUID> {

    /**
     * 포트폴리오의 Holding Baseline 조회
     * 포트폴리오당 1개만 존재함 (UNIQUE INDEX)
     */
    Optional<PortfolioHoldingBaseline> findByPortfolioId(UUID portfolioId);

    /**
     * 포트폴리오의 Holding Baseline 존재 여부 확인
     */
    boolean existsByPortfolioId(UUID portfolioId);

    /**
     * 포트폴리오의 Holding Baseline 삭제
     */
    void deleteByPortfolioId(UUID portfolioId);

    /**
     * Holding Baseline과 Items를 함께 조회 (N+1 방지)
     */
    @Query("SELECT b FROM PortfolioHoldingBaseline b " +
           "LEFT JOIN FETCH b.items " +
           "WHERE b.portfolioId = :portfolioId")
    Optional<PortfolioHoldingBaseline> findByPortfolioIdWithItems(@Param("portfolioId") UUID portfolioId);
}
