package com.porcana.domain.portfolio.repository;

import com.porcana.domain.asset.entity.QAsset;
import com.porcana.domain.portfolio.dto.PortfolioListResponse;
import com.porcana.domain.portfolio.entity.QSnapshotAssetDailyReturn;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Custom repository implementation for SnapshotAssetDailyReturn
 * Uses QueryDSL for complex queries
 */
@RequiredArgsConstructor
public class SnapshotAssetDailyReturnRepositoryImpl implements SnapshotAssetDailyReturnRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<PortfolioListResponse.TopAsset> findTopAssetsByWeight(UUID portfolioId, int limit) {
        QSnapshotAssetDailyReturn sadr = QSnapshotAssetDailyReturn.snapshotAssetDailyReturn;
        QSnapshotAssetDailyReturn sadr2 = new QSnapshotAssetDailyReturn("sadr2");
        QAsset asset = QAsset.asset;

        return queryFactory
                .select(Projections.constructor(
                        PortfolioListResponse.TopAsset.class,
                        asset.id,
                        asset.symbol,
                        asset.name,
                        asset.imageUrl,
                        sadr.weightUsed
                ))
                .from(sadr)
                .join(asset).on(sadr.assetId.eq(asset.id))
                .where(
                        sadr.portfolioId.eq(portfolioId),
                        sadr.returnDate.eq(
                                JPAExpressions
                                        .select(sadr2.returnDate.max())
                                        .from(sadr2)
                                        .where(sadr2.portfolioId.eq(portfolioId))
                        )
                )
                .orderBy(sadr.weightUsed.desc())
                .limit(limit)
                .fetch();
    }
}