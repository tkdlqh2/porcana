package com.porcana.domain.asset.dto;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.Sector;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

/**
 * 종목 라이브러리 응답 DTO
 */
@Getter
@Builder
@Schema(description = "종목 라이브러리 응답")
public class AssetLibraryResponse {

    @Schema(description = "종목 목록")
    private List<AssetItem> assets;

    @Schema(description = "전체 종목 수")
    private long totalCount;

    @Schema(description = "전체 페이지 수")
    private int totalPages;

    @Schema(description = "현재 페이지 (0부터 시작)")
    private int currentPage;

    @Schema(description = "페이지 크기")
    private int pageSize;

    @Schema(description = "다음 페이지 존재 여부")
    private boolean hasNext;

    public static AssetLibraryResponse from(Page<Asset> page) {
        return AssetLibraryResponse.builder()
                .assets(page.getContent().stream()
                        .map(AssetItem::from)
                        .toList())
                .totalCount(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "종목 정보")
    public static class AssetItem {

        @Schema(description = "종목 ID")
        private UUID assetId;

        @Schema(description = "티커/종목코드", example = "AAPL")
        private String symbol;

        @Schema(description = "종목명", example = "Apple Inc.")
        private String name;

        @Schema(description = "시장", example = "US")
        private Asset.Market market;

        @Schema(description = "종목 타입", example = "STOCK")
        private Asset.AssetType type;

        @Schema(description = "섹터 (STOCK용)")
        private Sector sector;

        @Schema(description = "섹터 한글명")
        private String sectorKorean;

        @Schema(description = "자산 클래스 (ETF용)")
        private AssetClass assetClass;

        @Schema(description = "현재 위험도 (1-5)")
        private Integer currentRiskLevel;

        @Schema(description = "종목 로고 URL")
        private String imageUrl;

        public static AssetItem from(Asset asset) {
            return AssetItem.builder()
                    .assetId(asset.getId())
                    .symbol(asset.getSymbol())
                    .name(asset.getName())
                    .market(asset.getMarket())
                    .type(asset.getType())
                    .sector(asset.getSector())
                    .sectorKorean(asset.getSector() != null ? asset.getSector().getKoreanName() : null)
                    .assetClass(asset.getAssetClass())
                    .currentRiskLevel(asset.getCurrentRiskLevel())
                    .imageUrl(asset.getImageUrl())
                    .build();
        }
    }
}