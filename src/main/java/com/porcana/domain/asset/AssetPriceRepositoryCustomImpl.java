package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.asset.entity.QAssetPrice;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AssetPrice QueryDSL Custom Repository Implementation
 */
@RequiredArgsConstructor
public class AssetPriceRepositoryCustomImpl implements AssetPriceRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AssetPrice> findLatestPricesByAssetIds(Collection<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Collections.emptyList();
        }

        QAssetPrice ap = QAssetPrice.assetPrice;
        QAssetPrice apSub = new QAssetPrice("apSub");

        // SELECT ap FROM AssetPrice ap
        // WHERE ap.asset.id IN :assetIds
        // AND ap.priceDate = (
        //     SELECT MAX(apSub.priceDate) FROM AssetPrice apSub
        //     WHERE apSub.asset.id = ap.asset.id
        // )
        return queryFactory
                .selectFrom(ap)
                .where(
                        ap.asset.id.in(assetIds),
                        ap.priceDate.eq(
                                JPAExpressions
                                        .select(apSub.priceDate.max())
                                        .from(apSub)
                                        .where(apSub.asset.id.eq(ap.asset.id))
                        )
                )
                .fetch();
    }
}