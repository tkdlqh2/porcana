---
name: entity-reference
description: Porcana entity structure and relationships reference. Use when working with database models.
disable-model-invocation: false
---

# Porcana Entity Reference

## Core Entities

### Asset (종목)

불변 레코드 형태로 관리하는 종목 데이터

```java
@Entity
@Table(name = "assets")
public class Asset {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Market market;  // KR, US

    private String symbol;  // AAPL, 005930
    private String name;

    @Enumerated(EnumType.STRING)
    private AssetType type;  // STOCK, ETF

    @Enumerated(EnumType.STRING)
    private Sector sector;  // 주식 전용, ETF는 null

    @Enumerated(EnumType.STRING)
    private AssetClass assetClass;  // ETF 전용, 주식은 null

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<UniverseTag> universeTags;  // [SP500, NASDAQ100]

    private Integer currentRiskLevel;  // 1-5 (1: Low, 5: High)
    private Boolean active;
    private LocalDate asOf;
    private String imageUrl;  // US만 제공
}
```

**Enums:**
- **Market**: KR, US
- **AssetType**: STOCK, ETF
- **Sector**: GICS 11개 섹터 (주식 전용)
  - INFORMATION_TECHNOLOGY, HEALTH_CARE, FINANCIALS, etc.
- **AssetClass**: ETF 분류 (ETF 전용)
  - EQUITY_INDEX, SECTOR, DIVIDEND, BOND, COMMODITY

### AssetPrice (가격 데이터)

일별 종가 및 거래량

```java
@Entity
@Table(name = "asset_prices")
public class AssetPrice {
    @Id
    private UUID id;

    @ManyToOne
    private Asset asset;

    private LocalDate priceDate;
    private BigDecimal price;
    private Long volume;

    private LocalDateTime createdAt;
}
```

**Unique Index**: `(asset_id, price_date)`

### AssetRiskHistory (위험도 이력)

주 단위 위험도 계산 이력

```java
@Entity
@Table(name = "asset_risk_history")
public class AssetRiskHistory {
    @Id
    private UUID id;

    @ManyToOne
    private Asset asset;

    private String week;  // YYYY-WW
    private Integer riskLevel;  // 1-5
    private BigDecimal riskScore;  // 0-100
    private BigDecimal volatility;
    private BigDecimal maxDrawdown;
    private BigDecimal worstDayReturn;

    @Type(JsonType.class)
    private Map<String, Object> factorsSnapshot;

    private LocalDateTime createdAt;
}
```

**Unique Index**: `(asset_id, week)`

### ExchangeRate (환율)

일일 환율 정보 (한국수출입은행)

```java
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private CurrencyCode currencyCode;  // USD, JPY, EUR, etc.

    private String currencyName;
    private BigDecimal baseRate;  // 매매기준율
    private BigDecimal buyRate;
    private BigDecimal sellRate;
    private LocalDate exchangeDate;

    private LocalDateTime createdAt;
}
```

**Unique Index**: `(currency_code, exchange_date)`
**Supported Currencies**: 44개 (USD, JPY, EUR, CNY, etc.)

### User (사용자)

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    private String email;
    private String passwordHash;  // OAuth는 null
    private String nickname;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;  // EMAIL, GOOGLE, APPLE

    private String oauthProviderId;  // OAuth 전용
    private UUID mainPortfolioId;  // nullable

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### GuestSession (게스트 세션)

비회원 임시 세션

```java
@Entity
@Table(name = "guest_sessions")
public class GuestSession {
    @Id
    private UUID id;

    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;  // 만료 판단용 (30일)
}
```

### Portfolio (포트폴리오)

```java
@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id
    private UUID id;

    // XOR: 둘 중 정확히 하나만 NOT NULL
    private UUID userId;  // nullable
    private UUID guestSessionId;  // nullable

    private String name;

    @Enumerated(EnumType.STRING)
    private PortfolioStatus status;  // DRAFT, ACTIVE, FINISHED

    private LocalDate startedAt;  // nullable (DRAFT는 null)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Check Constraint**: `(userId IS NULL XOR guestSessionId IS NULL)`

### PortfolioPosition (포지션)

포트폴리오 내 자산 비중

```java
@Entity
@Table(name = "portfolio_positions")
public class PortfolioPosition {
    @Id
    private UUID id;

    @ManyToOne
    private Portfolio portfolio;

    @ManyToOne
    private Asset asset;

    private BigDecimal weightPct;  // 0-100

    private LocalDateTime createdAt;
}
```

**Unique Index**: `(portfolio_id, asset_id)`

### ArenaSession (아레나 세션)

드래프트 세션 관리

```java
@Entity
@Table(name = "arena_sessions")
public class ArenaSession {
    @Id
    private UUID id;

    @ManyToOne
    private Portfolio portfolio;

    // XOR: 둘 중 정확히 하나만 NOT NULL
    private UUID userId;  // nullable
    private UUID guestSessionId;  // nullable

    @Enumerated(EnumType.STRING)
    private SessionStatus status;  // IN_PROGRESS, COMPLETED

    private Integer currentRound;  // 0-10

    @Enumerated(EnumType.STRING)
    private RiskProfile riskProfile;  // SAFE, BALANCED, AGGRESSIVE

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Sector> selectedSectors;

    @ElementCollection
    private List<UUID> deckAssetIds;  // 선택된 자산 (최대 10개)

    @ElementCollection
    private List<UUID> shownAssetIds;  // 제시된 자산 (중복 방지)

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
```

### ArenaRound (아레나 라운드)

각 라운드의 선택지 기록

```java
@Entity
@Table(name = "arena_rounds")
public class ArenaRound {
    @Id
    private UUID id;

    @ManyToOne
    private ArenaSession session;

    private Integer roundNumber;  // 0-10

    @Enumerated(EnumType.STRING)
    private RoundType roundType;  // PRE_ROUND, ASSET

    @ElementCollection
    private List<UUID> optionAssetIds;  // Round 0은 null

    private UUID pickedAssetId;  // nullable

    @Enumerated(EnumType.STRING)
    private RiskProfile pickedRiskProfile;  // Round 0만

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Sector> pickedSectors;  // Round 0만

    private LocalDateTime createdAt;
}
```

## Entity Relationships

```
User 1 --- * Portfolio (mainPortfolioId)
GuestSession 1 --- * Portfolio
Portfolio 1 --- * PortfolioPosition
Portfolio 1 --- 1 ArenaSession
Asset 1 --- * PortfolioPosition
Asset 1 --- * AssetPrice
Asset 1 --- * AssetRiskHistory
ArenaSession 1 --- * ArenaRound
```

## Key Constraints

1. **XOR Ownership**: Portfolio, ArenaSession
   - `(userId IS NULL XOR guestSessionId IS NULL)`
2. **Unique Constraints**:
   - `(asset_id, price_date)` - AssetPrice
   - `(asset_id, week)` - AssetRiskHistory
   - `(currency_code, exchange_date)` - ExchangeRate
   - `(portfolio_id, asset_id)` - PortfolioPosition
3. **Guest Limits**: 게스트당 최대 3개 포트폴리오