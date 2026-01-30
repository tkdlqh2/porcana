---
name: portfolio-domain
description: Portfolio domain deep dive - snapshots, daily returns, performance calculation. Use when working with portfolio performance tracking.
disable-model-invocation: false
---

# Portfolio Domain - 포트폴리오 수익률 추적

## Overview

포트폴리오의 수익률을 시간에 따라 추적하기 위한 엔티티 및 로직 설명

**핵심 개념:**
- **Snapshot**: 특정 시점의 포트폴리오 구성 (리밸런싱 이력)
- **DailyReturn**: 일별 수익률 계산 결과
- **weightUsed**: 시가총액 기반 동적 비중 (시간에 따라 변함)

## Core Entities

### PortfolioSnapshot (포트폴리오 스냅샷)

특정 시점의 포트폴리오 자산 구성을 기록 (리밸런싱 이력)

```java
@Entity
@Table(name = "portfolio_snapshots")
public class PortfolioSnapshot {
    @Id
    private UUID id;

    private UUID portfolioId;

    /**
     * 스냅샷 유효 시작일
     * 이 날짜부터 다음 스냅샷 전까지 이 구성이 유효함
     */
    private LocalDate effectiveDate;

    /**
     * 스냅샷 메모 (예: "Initial creation", "Rebalancing" 등)
     */
    private String note;

    private LocalDateTime createdAt;
}
```

**Unique Index**: `(portfolio_id, effective_date)`

**사용 시나리오:**
1. 포트폴리오 생성 시 → 초기 스냅샷 생성
2. 사용자가 비중 리밸런싱 → 새 스냅샷 생성
3. 수익률 계산 시 → `effectiveDate <= targetDate` 중 가장 최근 스냅샷 사용

### PortfolioSnapshotAsset (스냅샷 자산 구성)

특정 스냅샷 시점의 자산별 비중 정보

```java
@Entity
@Table(name = "portfolio_snapshot_assets")
public class PortfolioSnapshotAsset {
    @Id
    private UUID id;

    private UUID snapshotId;
    private UUID assetId;

    /**
     * 자산 비중 (%)
     * 예: 25.0 = 25%
     * ⚠️ 이것은 초기 설정 비중 (고정값)
     */
    private BigDecimal weight;  // 초기 비중
}
```

**Unique Index**: `(snapshot_id, asset_id)`

**중요:**
- `weight`는 **스냅샷 생성 시점의 초기 비중** (고정)
- 시간이 지나면서 가격 변동으로 실제 비중은 변함
- **실제 현재 비중**은 `SnapshotAssetDailyReturn.weightUsed`에서 조회

### PortfolioDailyReturn (포트폴리오 일별 수익률)

포트폴리오 전체의 일별 수익률 기록

```java
@Entity
@Table(name = "portfolio_daily_returns")
public class PortfolioDailyReturn {
    @Id
    private UUID id;

    private UUID portfolioId;
    private UUID snapshotId;  // 적용된 스냅샷
    private LocalDate returnDate;

    /**
     * 전체 수익률 (%)
     * return_total = return_local + return_fx
     */
    private BigDecimal returnTotal;

    /**
     * 로컬 가격 변동 수익률 (%)
     * 자산의 현지 통화 기준 가격 변동
     */
    private BigDecimal returnLocal;

    /**
     * 환율 수익률 (%)
     * 외화 자산의 환율 변동 효과
     */
    private BigDecimal returnFx;

    /**
     * 포트폴리오 전체 평가금액 (원화 기준)
     * 기준: 초기 10,000,000원 투자 가정
     * 예: 10,000,000 × (1 + 0.02) = 10,200,000원
     */
    private BigDecimal totalValueKrw;

    private LocalDateTime calculatedAt;
}
```

**Unique Index**: `(portfolio_id, return_date)`

**계산 예시:**
```
삼성전자 (KR): 로컬 +2%, 환율 0% → 전체 +2%
애플 (US): 로컬 +3%, 환율 +1% → 전체 +4%
포트폴리오: 가중평균 → return_total
```

### SnapshotAssetDailyReturn (자산별 일별 수익률)

포트폴리오 내 각 자산의 일별 수익률 및 **현재 비중** 기록

```java
@Entity
@Table(name = "snapshot_asset_daily_returns")
public class SnapshotAssetDailyReturn {
    @Id
    private UUID id;

    private UUID portfolioId;
    private UUID snapshotId;
    private UUID assetId;
    private LocalDate returnDate;

    /**
     * 🔥 사용된 자산 비중 (%) - 시가총액 기반 동적 비중
     * ⚠️ 이것이 실제 현재 비중! (가격 변동에 따라 매일 변함)
     */
    private BigDecimal weightUsed;

    /**
     * 자산 로컬 수익률 (%)
     * 자산의 현지 통화 기준 가격 변동률
     */
    private BigDecimal assetReturnLocal;

    /**
     * 자산 전체 수익률 (%)
     * asset_return_total = asset_return_local + fx_return
     */
    private BigDecimal assetReturnTotal;

    /**
     * 환율 수익률 (%)
     * 외화 자산의 환율 변동 효과 (KRW 자산은 0)
     */
    private BigDecimal fxReturn;

    /**
     * 포트폴리오 전체 수익률에 대한 기여도 (%)
     * contribution_total = asset_return_total * initialWeight / 100
     */
    private BigDecimal contributionTotal;

    /**
     * 자산 평가금액 (원화 기준)
     * 초기 비중 × 초기 투자금 × (1 + 수익률)
     * 예: 10% × 10,000,000원 × 1.20 = 1,200,000원
     */
    private BigDecimal valueKrw;

    private LocalDateTime calculatedAt;
}
```

**Unique Index**: `(portfolio_id, snapshot_id, asset_id, return_date)`

## weightUsed의 의미 🔥

### ❌ 이전 (잘못된 구현)
```java
// 스냅샷의 고정 비중을 그대로 복사
BigDecimal weight = snapshotAsset.getWeight();
```
- 시간이 지나도 비중이 변하지 않음
- 실제 시장 가치 반영 안 됨

### ✅ 현재 (올바른 구현 - 금액 기반)
```java
// 금액 기반 계산 → 비중 자동 산출
초기 투자금 = 10,000,000원
초기 비중 = 10%
초기 금액 = 10,000,000 × 0.10 = 1,000,000원

수익률 = +20%
현재 평가금액 = 1,000,000 × 1.20 = 1,200,000원 (valueKrw)

전체 평가금액 = 모든 자산 valueKrw 합계 (totalValueKrw)
현재 비중 = (1,200,000 / totalValueKrw) × 100 (weightUsed)
```

### 계산 예시

**포트폴리오 초기 구성:**
- 삼성전자: 10%
- 카카오: 10%
- 나머지: 80%

**1일 후:**
- 삼성전자: 수익률 +20% → 평가금액 12 → **현재 비중 약 11%**
- 카카오: 수익률 -10% → 평가금액 9 → **현재 비중 약 9%**

**결과:**
- API에서 조회 시 → 삼성 11%, 카카오 9%
- 수익률과 함께 실시간 비중 변화 확인 가능

## 수익률 계산 로직 (Batch)

### 1. 적용 스냅샷 찾기

```java
// effectiveDate <= targetDate 중 가장 최근 스냅샷 사용
PortfolioSnapshot snapshot =
    findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
        portfolioId, targetDate
    );
LocalDate snapshotDate = snapshot.getEffectiveDate();
```

### 2. 금액 기반 비중 계산 (Two-Pass)

```java
// 초기 가상 투자금
private static final BigDecimal INITIAL_INVESTMENT_KRW = new BigDecimal("10000000.00");

// First pass: 자산별 현재 평가금액 계산 (KRW)
BigDecimal totalCurrentValueKrw = BigDecimal.ZERO;
Map<UUID, BigDecimal> valueKrwMap = new HashMap<>();

for (각 자산) {
    초기비중 = snapshotAsset.getWeight();  // 예: 10.0%
    수익률 = calculateAssetReturn(asset, snapshotDate, targetDate);

    // 초기 투자 금액 계산
    초기금액 = INITIAL_INVESTMENT_KRW × (초기비중 / 100);  // 1,000,000원

    // 현재 평가금액 계산
    현재평가금액 = 초기금액 × (1 + 수익률/100);  // 1,200,000원

    valueKrwMap.put(assetId, 현재평가금액);
    totalCurrentValueKrw += 현재평가금액;
}

// Second pass: 비중 자동 계산 및 저장
for (각 자산) {
    valueKrw = valueKrwMap.get(assetId);  // 1,200,000원
    weightUsed = (valueKrw / totalCurrentValueKrw) × 100;  // 비중 자동 산출

    // SnapshotAssetDailyReturn에 weightUsed, valueKrw 모두 저장 ✅
}

// PortfolioDailyReturn에 totalValueKrw 저장 ✅
```

### 3. 환율 효과 분리

```java
// 미국 자산의 경우
assetReturnLocal = (targetPrice - startPrice) / startPrice × 100
fxReturn = (targetRate - startRate) / startRate × 100
assetReturnTotal = assetReturnLocal + fxReturn

// 한국 자산의 경우
fxReturn = 0
assetReturnTotal = assetReturnLocal
```

## API에서 사용

### HomeService, PortfolioService

```java
/**
 * Get latest market-cap based weights for assets
 * Returns the most recent weightUsed from SnapshotAssetDailyReturn
 */
private Map<UUID, Double> getLatestWeights(UUID portfolioId, Set<UUID> assetIds) {
    Map<UUID, Double> weights = new HashMap<>();

    for (UUID assetId : assetIds) {
        snapshotAssetDailyReturnRepository
            .findFirstByPortfolioIdAndAssetIdOrderByReturnDateDesc(portfolioId, assetId)
            .ifPresent(dailyReturn ->
                weights.put(assetId, dailyReturn.getWeightUsed().doubleValue())
            );
    }

    return weights;
}
```

**사용:**
```java
// 최신 weightUsed 조회, 없으면 초기 비중 사용
Double weightPct = latestWeights.getOrDefault(
    pa.getAssetId(),
    pa.getWeightPct().doubleValue()
);
```

## Entity Relationships

```
Portfolio 1 --- * PortfolioSnapshot
PortfolioSnapshot 1 --- * PortfolioSnapshotAsset
PortfolioSnapshot 1 --- * PortfolioDailyReturn
PortfolioSnapshot 1 --- * SnapshotAssetDailyReturn

Asset 1 --- * PortfolioSnapshotAsset
Asset 1 --- * SnapshotAssetDailyReturn
```

## Data Flow (수익률 계산)

```
1. 포트폴리오 생성
   └─> PortfolioSnapshot 생성 (effectiveDate = startedAt)
       └─> PortfolioSnapshotAsset 생성 (초기 비중)

2. 배치 실행 (매일 19:00)
   ├─> 적용 스냅샷 찾기
   ├─> 자산별 수익률 계산
   ├─> 시가총액 기반 비중 계산
   ├─> SnapshotAssetDailyReturn 저장 (weightUsed ✅)
   └─> PortfolioDailyReturn 저장

3. API 조회
   ├─> 최신 SnapshotAssetDailyReturn 조회
   └─> weightUsed를 현재 비중으로 표시
```

## Rebalancing (리밸런싱)

사용자가 수동으로 비중을 조정할 경우:

```
1. 사용자가 비중 변경 요청
   └─> PortfolioAsset.weightPct 업데이트 (기존 테이블)

2. 새 PortfolioSnapshot 생성
   ├─> effectiveDate = today
   ├─> note = "Portfolio rebalancing"
   └─> PortfolioSnapshotAsset 생성 (새 비중)

3. 이후 배치 실행
   └─> 새 스냅샷 기준으로 수익률 계산
```

## Key Points 요약

1. **금액 기반 계산 (가장 중요) 💰:**
   - 초기 투자금: **10,000,000원** 가정
   - `valueKrw`: 자산 평가금액 (원화) - 실제 금액
   - `totalValueKrw`: 포트폴리오 전체 평가금액 (원화)
   - `weightUsed`: 금액 기반으로 자동 계산된 비중 (%)

2. **두 종류의 비중:**
   - `PortfolioSnapshotAsset.weight`: 초기 설정 비중 (고정, %)
   - `SnapshotAssetDailyReturn.weightUsed`: 금액 기반 동적 비중 (매일 변함, %) ✅

3. **계산 흐름:**
   ```
   초기 비중(%) → 초기 금액(KRW) → 수익률 적용 → 현재 금액(KRW) → 현재 비중(%)
   ```

4. **API는 weightUsed 사용:**
   - 최신 weightUsed를 조회하여 현재 비중 표시
   - 가격 변동에 따른 실시간 비중 변화 반영

5. **배치는 Two-Pass 계산:**
   - First: 금액 계산 (valueKrw, totalValueKrw)
   - Second: 비중 자동 산출 (weightUsed)

6. **환율 효과 분리:**
   - 로컬 수익률 + 환율 수익률 = 전체 수익률
   - 환율 변동의 영향을 명확히 추적

7. **스냅샷 기반 이력 관리:**
   - 리밸런싱 이력 추적 가능
   - 과거 특정 시점의 구성 확인 가능

8. **확장성:**
   - 실제 거래 기능 추가 시 금액 필드 바로 활용 가능
   - 사용자별 초기 투자금 커스터마이징 가능