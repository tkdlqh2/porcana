package com.porcana.domain.asset.entity;

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
 * End-of-Day (EOD) price data for assets
 * Stores historical daily OHLC (Open, High, Low, Close) price information
 */
@Entity
@Table(name = "asset_prices", indexes = {
        @Index(name = "idx_asset_price_asset_date", columnList = "asset_id, price_date", unique = true),
        @Index(name = "idx_asset_price_date", columnList = "price_date"),
        @Index(name = "idx_asset_price_asset", columnList = "asset_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(nullable = false, name = "price_date")
    private LocalDate priceDate;

    /**
     * Opening price (시가)
     */
    @Column(name = "open_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal openPrice;

    /**
     * Highest price (고가)
     */
    @Column(name = "high_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal highPrice;

    /**
     * Lowest price (저가)
     */
    @Column(name = "low_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal lowPrice;

    /**
     * Closing price (종가)
     */
    @Column(name = "close_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal closePrice;

    /**
     * Trading volume (거래량)
     */
    @Column(nullable = false)
    private Long volume;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AssetPrice(Asset asset, LocalDate priceDate, BigDecimal openPrice, BigDecimal highPrice,
                      BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        this.asset = asset;
        this.priceDate = priceDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    /**
     * Legacy getter for backward compatibility
     * Returns close price as the representative price
     */
    public BigDecimal getPrice() {
        return closePrice;
    }
}