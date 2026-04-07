package com.porcana.domain.admin.dto.response;

import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import com.porcana.domain.user.entity.User;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Portfolio detail response for admin with owner info
 */
@Builder
public record AdminPortfolioDetailResponse(
        UUID portfolioId,
        String name,
        PortfolioStatus status,
        LocalDate startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        // Owner info
        OwnerInfo owner,
        // Assets
        List<AssetItem> assets,
        int assetCount
) {
    @Builder
    public record OwnerInfo(
            String ownerType,  // "USER" or "GUEST"
            UUID userId,
            String email,
            String nickname,
            UUID guestSessionId
    ) {
        public static OwnerInfo fromUser(User user) {
            return OwnerInfo.builder()
                    .ownerType("USER")
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .build();
        }

        public static OwnerInfo fromGuestSession(UUID guestSessionId) {
            return OwnerInfo.builder()
                    .ownerType("GUEST")
                    .guestSessionId(guestSessionId)
                    .build();
        }
    }

    @Builder
    public record AssetItem(
            UUID assetId,
            String symbol,
            String name,
            BigDecimal weightPct
    ) {
        public static AssetItem from(PortfolioAsset pa, String symbol, String name) {
            return AssetItem.builder()
                    .assetId(pa.getAssetId())
                    .symbol(symbol)
                    .name(name)
                    .weightPct(pa.getWeightPct())
                    .build();
        }
    }

    public static AdminPortfolioDetailResponse from(Portfolio portfolio, OwnerInfo owner,
                                                     List<AssetItem> assets) {
        return AdminPortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId())
                .name(portfolio.getName())
                .status(portfolio.getStatus())
                .startedAt(portfolio.getStartedAt())
                .createdAt(portfolio.getCreatedAt())
                .updatedAt(portfolio.getUpdatedAt())
                .deletedAt(portfolio.getDeletedAt())
                .owner(owner)
                .assets(assets)
                .assetCount(assets.size())
                .build();
    }
}
