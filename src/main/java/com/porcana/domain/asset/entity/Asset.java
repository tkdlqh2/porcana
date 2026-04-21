package com.porcana.domain.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assets", indexes = {
        @Index(name = "idx_asset_symbol_market", columnList = "symbol, market", unique = true),
        @Index(name = "idx_asset_active", columnList = "active"),
        @Index(name = "idx_asset_market", columnList = "market")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Market market;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Sector sector;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AssetClass assetClass;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "asset_universe_tags",
            joinColumns = @JoinColumn(name = "asset_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 50)
    private List<UniverseTag> universeTags = new ArrayList<>();

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "current_risk_level")
    private Integer currentRiskLevel;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 배당 관련 필드
    @Column(name = "dividend_available")
    private Boolean dividendAvailable;

    @Column(name = "dividend_yield", precision = 10, scale = 6)
    private BigDecimal dividendYield;

    @Enumerated(EnumType.STRING)
    @Column(name = "dividend_frequency", length = 20)
    private DividendFrequency dividendFrequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "dividend_category", length = 30)
    private DividendCategory dividendCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "dividend_data_status", length = 20)
    private DividendDataStatus dividendDataStatus;

    @Column(name = "last_dividend_date")
    private LocalDate lastDividendDate;

    @Column(nullable = false)
    private LocalDate asOf;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Asset(Market market, String symbol, String name, AssetType type, Sector sector,
                 AssetClass assetClass, List<UniverseTag> universeTags, Boolean active,
                 String imageUrl, String description, Boolean dividendAvailable, BigDecimal dividendYield,
                 DividendFrequency dividendFrequency, DividendCategory dividendCategory,
                 DividendDataStatus dividendDataStatus, LocalDate lastDividendDate,
                 LocalDate asOf) {
        this.market = market;
        this.symbol = symbol;
        this.name = name;
        this.type = type;
        this.sector = sector;
        this.assetClass = assetClass;
        this.universeTags = universeTags != null ? universeTags : new ArrayList<>();
        this.active = active != null ? active : false;
        this.imageUrl = imageUrl;
        this.description = description;
        this.dividendAvailable = dividendAvailable;
        this.dividendYield = dividendYield;
        this.dividendFrequency = dividendFrequency;
        this.dividendCategory = dividendCategory;
        this.dividendDataStatus = dividendDataStatus;
        this.lastDividendDate = lastDividendDate;
        this.asOf = asOf;
    }

    public void updateUniverseTags(List<UniverseTag> universeTags) {
        this.universeTags = universeTags != null ? universeTags : new ArrayList<>();
    }

    public void addUniverseTag(UniverseTag tag) {
        if (!this.universeTags.contains(tag)) {
            this.universeTags.add(tag);
        }
    }

    public void removeUniverseTag(UniverseTag tag) {
        this.universeTags.remove(tag);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateAsOf(LocalDate asOf) {
        this.asOf = asOf;
    }

    public void updateCurrentRiskLevel(Integer riskLevel) {
        if (riskLevel != null && (riskLevel < 1 || riskLevel > 5)) {
            throw new IllegalArgumentException("Risk level must be between 1 and 5");
        }
        this.currentRiskLevel = riskLevel;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 배당 데이터 일괄 업데이트
     *
     * @throws IllegalArgumentException 배당 수익률이 음수이거나 100%를 초과하는 경우,
     *                                  또는 마지막 배당일이 미래인 경우
     */
    public void updateDividendData(Boolean dividendAvailable, BigDecimal dividendYield,
                                   DividendFrequency dividendFrequency, DividendCategory dividendCategory,
                                   DividendDataStatus dividendDataStatus, LocalDate lastDividendDate) {
        // 배당 수익률 검증
        if (dividendYield != null) {
            if (dividendYield.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Dividend yield cannot be negative: " + dividendYield);
            }
            if (dividendYield.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Dividend yield cannot exceed 100%: " + dividendYield);
            }
        }

        // 마지막 배당일 검증
        if (lastDividendDate != null && lastDividendDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Last dividend date cannot be in the future: " + lastDividendDate);
        }

        this.dividendAvailable = dividendAvailable;
        this.dividendYield = dividendYield;
        this.dividendFrequency = dividendFrequency;
        this.dividendCategory = dividendCategory;
        this.dividendDataStatus = dividendDataStatus;
        this.lastDividendDate = lastDividendDate;
    }

    public enum Market {
        KR, US
    }

    public enum AssetType {
        STOCK, ETF
    }
}