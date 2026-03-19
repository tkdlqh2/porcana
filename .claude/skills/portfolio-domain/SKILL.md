---
name: portfolio-domain
description: Portfolio return calculation logic. Use when working with portfolio performance tracking.
disable-model-invocation: false
---

# Portfolio Domain - 수익률 계산 로직

## 핵심 개념

| 개념 | 설명 |
|------|------|
| **Snapshot** | 특정 시점의 포트폴리오 구성 (리밸런싱 이력) |
| **weightUsed** | 시가총액 기반 동적 비중 (시간에 따라 변함) |
| **초기 투자금** | 10,000,000원 가상 투자 가정 |

## 두 종류의 비중

| 필드 | 의미 | 특징 |
|------|------|------|
| `PortfolioSnapshotAsset.weight` | 초기 설정 비중 | 고정값 |
| `SnapshotAssetDailyReturn.weightUsed` | 금액 기반 동적 비중 | 매일 변함 ✅ |

**API는 항상 `weightUsed`를 현재 비중으로 표시**

## 금액 기반 비중 계산 (Two-Pass)

```
초기 비중(%) → 초기 금액(KRW) → 수익률 적용 → 현재 금액(KRW) → 현재 비중(%)
```

### 예시

**초기 구성:** 삼성전자 10%, 카카오 10%

**1일 후:**
- 삼성전자: +20% → 평가금액 1,200,000원 → **비중 약 11%**
- 카카오: -10% → 평가금액 900,000원 → **비중 약 9%**

### 계산 로직

```java
// First pass: 자산별 현재 평가금액 계산
초기금액 = 10,000,000 × (초기비중 / 100);  // 1,000,000원
현재평가금액 = 초기금액 × (1 + 수익률/100);  // 1,200,000원 (valueKrw)

// Second pass: 비중 자동 계산
weightUsed = (valueKrw / totalValueKrw) × 100;
```

## 환율 효과 분리

```
전체 수익률 = 로컬 수익률 + 환율 수익률
```

| 시장 | 계산 |
|------|------|
| US 자산 | `assetReturnTotal = assetReturnLocal + fxReturn` |
| KR 자산 | `assetReturnTotal = assetReturnLocal` (fxReturn = 0) |

## 리밸런싱 시 동작

1. 사용자가 비중 변경 → `PortfolioAsset.weightPct` 즉시 업데이트
2. 같은 날 스냅샷 생성/업데이트 (effectiveDate = today)
3. **UI 즉시 반영**: `weightUsed` 없으면 `weightPct` fallback
4. **다음 날 배치**: 새 스냅샷 기준으로 `weightUsed` 계산

## 스냅샷 찾기 로직

```java
// effectiveDate <= targetDate 중 가장 최근 스냅샷 사용
findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
    portfolioId, targetDate
);
```
