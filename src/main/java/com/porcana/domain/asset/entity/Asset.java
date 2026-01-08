package com.porcana.domain.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(length = 50)
    private String exchange;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetType type;

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

    @Column(nullable = false)
    private LocalDate asOf;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Asset(Market market, String symbol, String exchange, String name,
                 AssetType type, List<UniverseTag> universeTags, Boolean active, LocalDate asOf) {
        this.market = market;
        this.symbol = symbol;
        this.exchange = exchange;
        this.name = name;
        this.type = type;
        this.universeTags = universeTags != null ? universeTags : new ArrayList<>();
        this.active = active != null ? active : false;
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

    public enum Market {
        KR, US
    }

    public enum AssetType {
        STOCK, ETF
    }
}