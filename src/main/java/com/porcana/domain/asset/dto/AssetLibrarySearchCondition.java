package com.porcana.domain.asset.dto;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.Sector;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 종목 라이브러리 검색 조건
 */
@Getter
@Setter
public class AssetLibrarySearchCondition {

    @Parameter(description = "시장 (US, KR)")
    private Asset.Market market;

    @Parameter(description = "종목 타입 (STOCK, ETF)")
    private Asset.AssetType type;

    @Parameter(description = "섹터 (STOCK용, 복수 선택 가능)")
    private List<Sector> sectors;

    @Parameter(description = "자산 클래스 (ETF용, 복수 선택 가능)")
    private List<AssetClass> assetClasses;

    @Parameter(description = "위험도 레벨 (1-5, 복수 선택 가능)")
    private List<Integer> riskLevels;

    @Parameter(description = "검색어 (symbol 또는 name)")
    private String query;

    @Parameter(description = "정렬 기준 (name, symbol, riskLevel)")
    private String sortBy;

    @Parameter(description = "정렬 방향 (asc, desc)")
    private String sortDirection;
}