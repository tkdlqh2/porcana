package com.porcana.domain.asset;

import com.porcana.domain.asset.entity.AssetPrice;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * AssetPrice QueryDSL Custom Repository
 */
public interface AssetPriceRepositoryCustom {

    /**
     * 여러 자산의 최신 가격을 한 번에 조회 (N+1 쿼리 방지)
     * @param assetIds 조회할 자산 ID 목록
     * @return 각 자산의 최신 가격 리스트 (자산당 1개)
     */
    List<AssetPrice> findLatestPricesByAssetIds(Collection<UUID> assetIds);

    /**
     * 여러 자산의 날짜 범위 내 가격을 한 번에 조회 (N+1 쿼리 방지)
     * @param assetIds 조회할 자산 ID 목록
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 해당 범위의 모든 가격 리스트
     */
    List<AssetPrice> findPricesByAssetIdsAndDateRange(Collection<UUID> assetIds, LocalDate startDate, LocalDate endDate);
}