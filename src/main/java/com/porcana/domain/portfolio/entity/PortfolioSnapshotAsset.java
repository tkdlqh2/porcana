package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 포트폴리오 스냅샷 자산 구성
 * 특정 스냅샷 시점의 자산별 비중 정보
 */
@Entity
@Table(name = "portfolio_snapshot_assets", indexes = {
        @Index(name = "idx_portfolio_snapshot_asset_snapshot_id", columnList = "snapshot_id"),
        @Index(name = "idx_portfolio_snapshot_asset_asset_id", columnList = "asset_id"),
        @Index(name = "idx_portfolio_snapshot_asset_snapshot_asset", columnList = "snapshot_id, asset_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioSnapshotAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 자산 비중 (%)
     * 예: 25.0 = 25%
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Builder
    public PortfolioSnapshotAsset(UUID snapshotId, UUID assetId, BigDecimal weight) {
        this.snapshotId = snapshotId;
        this.assetId = assetId;
        this.weight = weight;
    }
}