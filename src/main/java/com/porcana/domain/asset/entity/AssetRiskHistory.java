package com.porcana.domain.asset.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 종목 위험도 이력 Entity
 * 주 단위로 위험도 계산 결과를 저장
 */
@Entity
@Table(
        name = "asset_risk_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "week"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AssetRiskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /**
     * 주차 (YYYY-WW 포맷, 예: "2025-W03")
     */
    @Column(nullable = false, length = 8)
    private String week;

    /**
     * 위험도 레벨 (1~5)
     * 1: Low, 2-4: Medium, 5: High
     */
    @Column(name = "risk_level", nullable = false)
    @Min(1)
    @Max(5)
    private Integer riskLevel;

    /**
     * 위험도 점수 (0~100)
     */
    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    /**
     * 변동성 (Volatility) - 연율화된 표준편차
     */
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal volatility;

    /**
     * 최대낙폭 (Max Drawdown)
     */
    @Column(name = "max_drawdown", nullable = false, precision = 10, scale = 6)
    private BigDecimal maxDrawdown;

    /**
     * 1일 최악 하락률 (Worst Day Return)
     */
    @Column(name = "worst_day_return", nullable = false, precision = 10, scale = 6)
    private BigDecimal worstDayReturn;

    /**
     * 계산에 사용된 추가 요소 스냅샷 (JSON)
     */
    @Column(name = "factors_snapshot", columnDefinition = "TEXT")
    private String factorsSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}