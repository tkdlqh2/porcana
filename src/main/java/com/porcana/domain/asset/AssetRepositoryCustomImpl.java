package com.porcana.domain.asset;

import com.porcana.domain.asset.dto.AssetLibrarySearchCondition;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.QAsset;
import com.porcana.domain.asset.entity.Sector;
import com.querydsl.core.types.OrderSpecifier;
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
 * Asset Repository Custom Implementation using QueryDSL
 */
@RequiredArgsConstructor
public class AssetRepositoryCustomImpl implements AssetRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Asset> searchLibrary(AssetLibrarySearchCondition condition, Pageable pageable) {
        QAsset asset = QAsset.asset;

        // 메인 쿼리
        List<Asset> content = queryFactory
                .selectFrom(asset)
                .where(
                        activeTrue(),
                        marketEq(condition.getMarket()),
                        typeEq(condition.getType()),
                        sectorsIn(condition.getSectors()),
                        assetClassesIn(condition.getAssetClasses()),
                        riskLevelsIn(condition.getRiskLevels()),
                        queryContains(condition.getQuery())
                )
                .orderBy(getOrderSpecifier(condition.getSortBy(), condition.getSortDirection()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 카운트 쿼리
        JPAQuery<Long> countQuery = queryFactory
                .select(asset.count())
                .from(asset)
                .where(
                        activeTrue(),
                        marketEq(condition.getMarket()),
                        typeEq(condition.getType()),
                        sectorsIn(condition.getSectors()),
                        assetClassesIn(condition.getAssetClasses()),
                        riskLevelsIn(condition.getRiskLevels()),
                        queryContains(condition.getQuery())
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression activeTrue() {
        return QAsset.asset.active.isTrue();
    }

    private BooleanExpression marketEq(Asset.Market market) {
        return market != null ? QAsset.asset.market.eq(market) : null;
    }

    private BooleanExpression typeEq(Asset.AssetType type) {
        return type != null ? QAsset.asset.type.eq(type) : null;
    }

    private BooleanExpression sectorsIn(List<Sector> sectors) {
        return sectors != null && !sectors.isEmpty() ? QAsset.asset.sector.in(sectors) : null;
    }

    private BooleanExpression assetClassesIn(List<AssetClass> assetClasses) {
        return assetClasses != null && !assetClasses.isEmpty() ? QAsset.asset.assetClass.in(assetClasses) : null;
    }

    private BooleanExpression riskLevelsIn(List<Integer> riskLevels) {
        return riskLevels != null && !riskLevels.isEmpty() ? QAsset.asset.currentRiskLevel.in(riskLevels) : null;
    }

    private BooleanExpression queryContains(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String lowerQuery = query.toLowerCase();
        return QAsset.asset.symbol.lower().contains(lowerQuery)
                .or(QAsset.asset.name.lower().contains(lowerQuery));
    }

    private OrderSpecifier<?> getOrderSpecifier(String sortBy, String sortDirection) {
        QAsset asset = QAsset.asset;
        boolean isDesc = "desc".equalsIgnoreCase(sortDirection);

        if (sortBy == null) {
            // 기본 정렬: symbol 오름차순
            return asset.symbol.asc();
        }

        return switch (sortBy.toLowerCase()) {
            case "name" -> isDesc ? asset.name.desc() : asset.name.asc();
            case "risklevel", "risk" -> isDesc ? asset.currentRiskLevel.desc() : asset.currentRiskLevel.asc();
            case "symbol" -> isDesc ? asset.symbol.desc() : asset.symbol.asc();
            default -> asset.symbol.asc();
        };
    }

    @Override
    public Page<Asset> searchByKeyword(String keyword, Pageable pageable) {
        QAsset asset = QAsset.asset;

        List<Asset> content = queryFactory
                .selectFrom(asset)
                .where(keywordContainsForAdmin(keyword))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(asset.symbol.asc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(asset.count())
                .from(asset)
                .where(keywordContainsForAdmin(keyword));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<Asset> searchForAdmin(String keyword, Asset.Market market, Asset.AssetType type, Pageable pageable) {
        QAsset asset = QAsset.asset;

        List<Asset> content = queryFactory
                .selectFrom(asset)
                .where(
                        keywordContainsForAdmin(keyword),
                        marketEq(market),
                        typeEq(type)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(asset.symbol.asc())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(asset.count())
                .from(asset)
                .where(
                        keywordContainsForAdmin(keyword),
                        marketEq(market),
                        typeEq(type)
                );

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression keywordContainsForAdmin(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String lowerKeyword = keyword.toLowerCase();
        return QAsset.asset.symbol.lower().contains(lowerKeyword)
                .or(QAsset.asset.name.lower().contains(lowerKeyword));
    }
}
