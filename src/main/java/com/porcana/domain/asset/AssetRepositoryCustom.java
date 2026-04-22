package com.porcana.domain.asset;

import com.porcana.domain.asset.dto.AssetLibrarySearchCondition;
import com.porcana.domain.asset.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Asset Repository Custom Interface for QueryDSL
 */
public interface AssetRepositoryCustom {

    /**
     * 종목 라이브러리 검색 (동적 필터링 + 페이지네이션)
     */
    Page<Asset> searchLibrary(AssetLibrarySearchCondition condition, Pageable pageable);

    /**
     * Search assets by symbol or name (for admin - includes inactive)
     */
    Page<Asset> searchByKeyword(String keyword, Pageable pageable);

    /**
     * Search assets for admin with optional keyword, market, and type filters
     */
    Page<Asset> searchForAdmin(String keyword, Asset.Market market, Asset.AssetType type, Pageable pageable);
}
