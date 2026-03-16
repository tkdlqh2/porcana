package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Holding Baseline의 개별 종목 보유 수량
 */
@Entity
@Table(name = "portfolio_holding_baseline_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioHoldingBaselineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baseline_id", nullable = false)
    private PortfolioHoldingBaseline baseline;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 보유 수량
     */
    @Column(name = "quantity", precision = 18, scale = 6, nullable = false)
    private BigDecimal quantity;

    /**
     * 평균 매수가 (선택)
     */
    @Column(name = "avg_price", precision = 18, scale = 4)
    private BigDecimal avgPrice;

    /**
     * baseline 생성 당시 목표 비중 (참고용)
     */
    @Column(name = "target_weight_pct", precision = 5, scale = 2)
    private BigDecimal targetWeightPct;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioHoldingBaselineItem(PortfolioHoldingBaseline baseline, UUID assetId,
                                          BigDecimal quantity, BigDecimal avgPrice, BigDecimal targetWeightPct) {
        this.baseline = baseline;
        this.assetId = assetId;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.targetWeightPct = targetWeightPct;
    }

    public static PortfolioHoldingBaselineItem create(PortfolioHoldingBaseline baseline, UUID assetId,
                                                       BigDecimal quantity, BigDecimal avgPrice, BigDecimal targetWeightPct) {
        if (baseline == null) {
            throw new IllegalArgumentException("baseline must not be null");
        }
        if (assetId == null) {
            throw new IllegalArgumentException("assetId must not be null");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }

        return PortfolioHoldingBaselineItem.builder()
                .baseline(baseline)
                .assetId(assetId)
                .quantity(quantity)
                .avgPrice(avgPrice)
                .targetWeightPct(targetWeightPct)
                .build();
    }
}
