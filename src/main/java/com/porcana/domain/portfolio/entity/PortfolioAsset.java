package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_assets", indexes = {
        @Index(name = "idx_portfolio_assets_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_portfolio_assets_asset_id", columnList = "asset_id"),
        @Index(name = "idx_portfolio_assets_portfolio_asset", columnList = "portfolio_id, asset_id", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "weight_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPct;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Builder
    public PortfolioAsset(UUID portfolioId, UUID assetId, BigDecimal weightPct) {
        this.portfolioId = portfolioId;
        this.assetId = assetId;
        this.weightPct = weightPct;
    }

    public void setWeightPct(BigDecimal weightPct) {
        this.weightPct = weightPct;
    }
}
