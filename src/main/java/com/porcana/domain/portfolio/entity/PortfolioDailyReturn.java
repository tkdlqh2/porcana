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
 * 포트폴리오 일별 수익률
 * 포트폴리오의 일별 수익률을 total, local, fx로 분리하여 저장
 */
@Entity
@Table(name = "portfolio_daily_returns", indexes = {
        @Index(name = "idx_portfolio_daily_return_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_portfolio_daily_return_snapshot_id", columnList = "snapshot_id"),
        @Index(name = "idx_portfolio_daily_return_date", columnList = "return_date"),
        @Index(name = "idx_portfolio_daily_return_portfolio_date", columnList = "portfolio_id, return_date", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioDailyReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    /**
     * 이 일별 수익률 계산에 사용된 스냅샷
     */
    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    /**
     * 수익률 기준일
     */
    @Column(nullable = false, name = "return_date")
    private LocalDate returnDate;

    /**
     * 전체 수익률 (%)
     * return_total = return_local + return_fx
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "return_total")
    private BigDecimal returnTotal;

    /**
     * 로컬 수익률 (%) - 환율 효과 제외
     * 각 자산의 현지 통화 기준 가격 변동률
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "return_local")
    private BigDecimal returnLocal;

    /**
     * 환율 수익률 (%)
     * 외화 자산의 환율 변동으로 인한 수익률
     */
    @Column(nullable = false, precision = 10, scale = 4, name = "return_fx")
    private BigDecimal returnFx;

    /**
     * 계산 완료 시각
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    public PortfolioDailyReturn(UUID portfolioId, UUID snapshotId, LocalDate returnDate,
                                BigDecimal returnTotal, BigDecimal returnLocal, BigDecimal returnFx) {
        this.portfolioId = portfolioId;
        this.snapshotId = snapshotId;
        this.returnDate = returnDate;
        this.returnTotal = returnTotal;
        this.returnLocal = returnLocal;
        this.returnFx = returnFx;
    }

    public static PortfolioDailyReturn from(UUID portfolioId, UUID snapshotId, LocalDate returnDate,
                                            BigDecimal returnTotal, BigDecimal returnLocal, BigDecimal returnFx) {
        return PortfolioDailyReturn.builder()
                .portfolioId(portfolioId)
                .snapshotId(snapshotId)
                .returnDate(returnDate)
                .returnTotal(returnTotal)
                .returnLocal(returnLocal)
                .returnFx(returnFx)
                .build();
    }
}