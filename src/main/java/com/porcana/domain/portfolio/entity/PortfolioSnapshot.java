package com.porcana.domain.portfolio.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 포트폴리오 스냅샷
 * 특정 시점의 포트폴리오 자산 구성을 기록
 */
@Entity
@Table(name = "portfolio_snapshots", indexes = {
        @Index(name = "idx_portfolio_snapshot_portfolio_id", columnList = "portfolio_id"),
        @Index(name = "idx_portfolio_snapshot_effective_date", columnList = "effective_date"),
        @Index(name = "idx_portfolio_snapshot_portfolio_date", columnList = "portfolio_id, effective_date", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    /**
     * 스냅샷 유효 시작일
     * 이 날짜부터 다음 스냅샷 전까지 이 구성이 유효함
     */
    @Column(nullable = false, name = "effective_date")
    private LocalDate effectiveDate;

    /**
     * 스냅샷 메모 (예: "Initial creation", "Rebalancing" 등)
     */
    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioSnapshot(UUID portfolioId, LocalDate effectiveDate, String note) {
        this.portfolioId = portfolioId;
        this.effectiveDate = effectiveDate;
        this.note = note;
    }

    /**
     * Create a portfolio snapshot
     */
    public static PortfolioSnapshot create(UUID portfolioId, LocalDate effectiveDate, String note) {
        if (portfolioId == null) {
            throw new IllegalArgumentException("portfolioId must not be null");
        }
        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate must not be null");
        }

        return PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .effectiveDate(effectiveDate)
                .note(note)
                .build();
    }
}