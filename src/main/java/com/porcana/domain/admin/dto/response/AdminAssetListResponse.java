package com.porcana.domain.admin.dto.response;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendDataStatus;
import com.porcana.domain.asset.entity.DividendFrequency;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Paginated asset list response for admin
 */
@Builder
public record AdminAssetListResponse(
        List<AssetItem> assets,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    @Builder
    public record AssetItem(
            UUID assetId,
            String symbol,
            String name,
            Asset.Market market,
            Asset.AssetType type,
            String sector,
            String assetClass,
            Boolean active,
            Integer currentRiskLevel,
            String imageUrl,
            // Dividend info
            Boolean dividendAvailable,
            BigDecimal dividendYield,
            DividendFrequency dividendFrequency,
            DividendCategory dividendCategory,
            DividendDataStatus dividendDataStatus,
            LocalDate lastDividendDate
    ) {
        public static AssetItem from(Asset asset) {
            return AssetItem.builder()
                    .assetId(asset.getId())
                    .symbol(asset.getSymbol())
                    .name(asset.getName())
                    .market(asset.getMarket())
                    .type(asset.getType())
                    .sector(asset.getSector() != null ? asset.getSector().name() : null)
                    .assetClass(asset.getAssetClass() != null ? asset.getAssetClass().name() : null)
                    .active(asset.getActive())
                    .currentRiskLevel(asset.getCurrentRiskLevel())
                    .imageUrl(asset.getImageUrl())
                    .dividendAvailable(asset.getDividendAvailable())
                    .dividendYield(asset.getDividendYield())
                    .dividendFrequency(asset.getDividendFrequency())
                    .dividendCategory(asset.getDividendCategory())
                    .dividendDataStatus(asset.getDividendDataStatus())
                    .lastDividendDate(asset.getLastDividendDate())
                    .build();
        }
    }

    public static AdminAssetListResponse from(Page<Asset> page) {
        return AdminAssetListResponse.builder()
                .assets(page.getContent().stream().map(AssetItem::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
