package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 스냅샷 자산별 일별 수익률 세부 내역
 * 포트폴리오 수익률의 자산별 기여도를 추적
 */
@Entity
@Table(name = "snapshot_asset_daily_returns", indexes = {
        @Index(name = "idx_snapshot_asset_daily_return_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_snapshot_asset_daily_return_snapshot_id", columnList = "snapshot_id"),
        @Index(name = "idx_snapshot_asset_daily_return_asset_id", columnList = "asset_id"),
        @Index(name = "idx_snapshot_asset_daily_return_date", columnList = "return_date"),
        @Index(name = "idx_snapshot_asset_daily_return_unique",
               columnList = "portfolio_id, snapshot_id, asset_id, return_date", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SnapshotAssetDailyReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 수익률 기준일
     */
    @Column(nullable = false, name = "return_date")
    private LocalDate returnDate;

    /**
     * 사용된 자산 비중 (%)
     */
    @Column(nullable = false, precision = 5, scale = 2, name = "weight_used")
    private BigDecimal weightUsed;

    /**
     * 자산 로컬 수익률 (%)
     * 자산의 현지 통화 기준 가격 변동률
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "asset_return_local")
    private BigDecimal assetReturnLocal;

    /**
     * 자산 전체 수익률 (%)
     * asset_return_total = asset_return_local + fx_return
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "asset_return_total")
    private BigDecimal assetReturnTotal;

    /**
     * 환율 수익률 (%)
     * 외화 자산의 환율 변동 효과 (KRW 자산은 0)
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "fx_return")
    private BigDecimal fxReturn;

    /**
     * 포트폴리오 전체 수익률에 대한 기여도 (%)
     * contribution_total = asset_return_total * weight_used / 100
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "contribution_total")
    private BigDecimal contributionTotal;

    /**
     * 계산 완료 시각
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    public SnapshotAssetDailyReturn(UUID portfolioId, UUID snapshotId, UUID assetId, LocalDate returnDate,
                                    BigDecimal weightUsed, BigDecimal assetReturnLocal, BigDecimal assetReturnTotal,
                                    BigDecimal fxReturn, BigDecimal contributionTotal) {
        this.portfolioId = portfolioId;
        this.snapshotId = snapshotId;
        this.assetId = assetId;
        this.returnDate = returnDate;
        this.weightUsed = weightUsed;
        this.assetReturnLocal = assetReturnLocal;
        this.assetReturnTotal = assetReturnTotal;
        this.fxReturn = fxReturn;
        this.contributionTotal = contributionTotal;
    }

    public static SnapshotAssetDailyReturn from(UUID portfolioId, UUID snapshotId, UUID assetId, LocalDate returnDate,
                                                BigDecimal weightUsed, BigDecimal assetReturnLocal,
                                                BigDecimal assetReturnTotal, BigDecimal fxReturn,
                                                BigDecimal contributionTotal) {
        return SnapshotAssetDailyReturn.builder()
                .portfolioId(portfolioId)
                .snapshotId(snapshotId)
                .assetId(assetId)
                .returnDate(returnDate)
                .weightUsed(weightUsed)
                .assetReturnLocal(assetReturnLocal)
                .assetReturnTotal(assetReturnTotal)
                .fxReturn(fxReturn)
                .contributionTotal(contributionTotal)
                .build();
    }
}