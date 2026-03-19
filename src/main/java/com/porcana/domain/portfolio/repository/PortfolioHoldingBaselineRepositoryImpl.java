package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import com.porcana.domain.portfolio.entity.QPortfolioHoldingBaseline;
import com.porcana.domain.portfolio.entity.QPortfolioHoldingBaselineItem;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.UUID;

/**
 * PortfolioHoldingBaseline Repository Custom Implementation using QueryDSL
 */
@RequiredArgsConstructor
public class PortfolioHoldingBaselineRepositoryImpl implements PortfolioHoldingBaselineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<PortfolioHoldingBaseline> findByPortfolioIdWithItems(UUID portfolioId) {
        QPortfolioHoldingBaseline baseline = QPortfolioHoldingBaseline.portfolioHoldingBaseline;
        QPortfolioHoldingBaselineItem item = QPortfolioHoldingBaselineItem.portfolioHoldingBaselineItem;

        PortfolioHoldingBaseline result = queryFactory
                .selectDistinct(baseline)
                .from(baseline)
                .leftJoin(baseline.items, item).fetchJoin()
                .where(baseline.portfolioId.eq(portfolioId))
                .fetchOne();

        return Optional.ofNullable(result);
    }
}