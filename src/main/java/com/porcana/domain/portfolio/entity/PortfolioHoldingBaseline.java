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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 포트폴리오 Holding Baseline
 * 사용자가 실제로 보유하고 있는 수량을 기록한 스냅샷
 */
@Entity
@Table(name = "portfolio_holding_baselines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PortfolioHoldingBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_currency", nullable = false, length = 10)
    private Currency baseCurrency;

    @Column(name = "cash_amount", precision = 18, scale = 2)
    private BigDecimal cashAmount;

    @Column(name = "memo", length = 255)
    private String memo;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "baseline", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioHoldingBaselineItem> items = new ArrayList<>();

    public enum SourceType {
        MANUAL,      // 사용자가 직접 입력
        SEEDED,      // 시드 금액으로 계산
        BROKER_SYNC  // 증권사 연동 (미래)
    }

    public enum Currency {
        KRW,
        USD
    }

    @Builder(access = AccessLevel.PRIVATE)
    private PortfolioHoldingBaseline(UUID portfolioId, UUID userId, SourceType sourceType,
                                     Currency baseCurrency, BigDecimal cashAmount, String memo) {
        this.portfolioId = portfolioId;
        this.userId = userId;
        this.sourceType = sourceType;
        this.baseCurrency = baseCurrency;
        this.cashAmount = cashAmount;
        this.memo = memo;
        this.confirmedAt = LocalDateTime.now();
    }

    public static PortfolioHoldingBaseline create(UUID portfolioId, UUID userId, SourceType sourceType,
                                                   Currency baseCurrency, BigDecimal cashAmount, String memo) {
        if (portfolioId == null) {
            throw new IllegalArgumentException("portfolioId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType must not be null");
        }
        if (baseCurrency == null) {
            baseCurrency = Currency.KRW;
        }
        if (cashAmount == null) {
            cashAmount = BigDecimal.ZERO;
        }

        return PortfolioHoldingBaseline.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .sourceType(sourceType)
                .baseCurrency(baseCurrency)
                .cashAmount(cashAmount)
                .memo(memo)
                .build();
    }

    /**
     * Baseline에 종목 추가
     */
    public PortfolioHoldingBaselineItem addItem(UUID assetId, BigDecimal quantity,
                                                 BigDecimal avgPrice, BigDecimal targetWeightPct) {
        PortfolioHoldingBaselineItem item = PortfolioHoldingBaselineItem.create(
                this, assetId, quantity, avgPrice, targetWeightPct);
        this.items.add(item);
        return item;
    }

    /**
     * 모든 아이템 교체 (전체 업데이트 시 사용)
     */
    public void replaceItems(List<PortfolioHoldingBaselineItem> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
    }

    /**
     * Baseline 정보 업데이트
     */
    public void update(SourceType sourceType, Currency baseCurrency, BigDecimal cashAmount, String memo) {
        this.sourceType = sourceType;
        this.baseCurrency = baseCurrency;
        this.cashAmount = cashAmount != null ? cashAmount : BigDecimal.ZERO;
        this.memo = memo;
        this.confirmedAt = LocalDateTime.now();
    }
}
