package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioHoldingBaselineRepository extends JpaRepository<PortfolioHoldingBaseline, UUID>,
        PortfolioHoldingBaselineRepositoryCustom {

    /**
     * ?ы듃?대━?ㅼ쓽 Holding Baseline 議고쉶
     * ?ы듃?대━?ㅻ떦 1媛쒕쭔 議댁옱??(UNIQUE INDEX)
     */
    Optional<PortfolioHoldingBaseline> findByPortfolioId(UUID portfolioId);

    /**
     * ?ы듃?대━?ㅼ쓽 Holding Baseline 議댁옱 ?щ? ?뺤씤
     */
    boolean existsByPortfolioId(UUID portfolioId);

    /**
     * 포트폴리오 목록에서 baseline 존재 포트폴리오 ID를 일괄 조회
     */
    @Query("SELECT b.portfolioId FROM PortfolioHoldingBaseline b WHERE b.portfolioId IN :portfolioIds")
    List<UUID> findPortfolioIdsByPortfolioIdIn(List<UUID> portfolioIds);

    /**
     * ?ы듃?대━?ㅼ쓽 Holding Baseline ??젣
     */
    void deleteByPortfolioId(UUID portfolioId);
}
