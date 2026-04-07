package com.porcana.domain.portfolio.repository;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.QPortfolio;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Portfolio Repository Custom Implementation using QueryDSL
 */
@RequiredArgsConstructor
public class PortfolioRepositoryCustomImpl implements PortfolioRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Portfolio> searchByName(String keyword, Pageable pageable) {
        QPortfolio portfolio = QPortfolio.portfolio;

        List<Portfolio> content = queryFactory
                .selectFrom(portfolio)
                .where(
                        deletedAtIsNull(),
                        nameContains(keyword)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(portfolio.createdAt.desc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(portfolio.count())
                .from(portfolio)
                .where(
                        deletedAtIsNull(),
                        nameContains(keyword)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression deletedAtIsNull() {
        return QPortfolio.portfolio.deletedAt.isNull();
    }

    private BooleanExpression nameContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return QPortfolio.portfolio.name.lower().contains(keyword.toLowerCase());
    }
}
