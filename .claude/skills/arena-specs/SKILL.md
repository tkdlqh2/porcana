---
name: arena-specs
description: Arena asset recommendation algorithm specification. Use when working with Arena logic.
disable-model-invocation: false
---

# Arena Asset Recommendation Algorithm

## Round 구조

- **Round 0 (Pre Round)**: Risk Profile + Sector 동시 선택
  - Risk Profile: SAFE | BALANCED | AGGRESSIVE (1개 필수)
  - Sectors: 0-3개 선택 가능 (GICS 표준 섹터)
- **Rounds 1-10**: Asset 선택 (라운드당 3개 중 1개)

## 추천 알고리즘 핵심

### Entry Point
```java
List<Asset> generateRoundOptions(ArenaSession session, int roundNo)
```

### 선택 전략

#### 1) Normal Picks (2개)
- 선호 섹터 + 리스크 프로필 + 다양성 반영
- `preferredCandidates`에서 선택

#### 2) Wild Pick (1개)
- 섹터 선호 무시, 의외성 보장
- `wildCandidates`에서 선택

### Weight Calculation

```java
w = 1.0
w *= riskWeight(riskProfile, asset.currentRiskLevel);
w *= sectorWeight(preferredSectors, asset.sector);  // normal picks만
w *= typeWeight(asset.type);
w *= diversityPenalty(asset, alreadyPicked);
w = max(w, 0.0001);  // 안전장치
```

## Weight Functions

### Risk Weight
```java
if profile == SAFE:
    riskLevel [1,2]: 1.4x
    riskLevel [3]: 1.0x
    riskLevel [4,5]: 0.6x

if profile == BALANCED:
    riskLevel [2,3,4]: 1.2x
    riskLevel [1,5]: 0.8x

if profile == AGGRESSIVE:
    riskLevel [4,5]: 1.4x
    riskLevel [3]: 1.0x
    riskLevel [1,2]: 0.7x
```

### Sector Weight
```java
if preferredSectors.contains(sector): 1.5x
else: 1.0x
```

### Type Weight
```java
ETF: 2.5x (강한 선호로 다양성 확보)
STOCK: 1.0x

Wild Pick에서 ETF: 2.5 × 1.5 = 3.75x
```

### Diversity Penalty
```java
// 같은 섹터면 크게 감소
if alreadyPicked.any(p => p.sector == candidate.sector):
    penalty *= 0.35

// 같은 리스크 밴드면 조금 감소
// LOW(1-2) | MID(3) | HIGH(4-5)
if alreadyPicked.any(p => riskBand(p) == riskBand(candidate)):
    penalty *= 0.70
```

## Diversity Condition

라운드당 3개 선택지 조건:
- **최소 2개 섹터**
- **최소 2개 리스크 밴드**

실패 시 최대 5회 재시도, `shownAssetIds` 제약 완화

## Query Optimization (Bucket Sampling)

### Bucket Sizes
- **Preferred Sector Candidates**: 80개
- **Non-Preferred Sector Candidates**: 40개
- **Wild Candidates**: 20개
- **Total**: ~140개 (전체 대비 86% 절감)

### Sampling Strategy (PK Range Random)
```sql
WHERE a.id >= :rand_id
ORDER BY a.id
LIMIT N
```

**장점:**
- 빠름 (인덱스 활용)
- `ORDER BY random()` 금지
- MVP에 충분

## Fallback Strategy

후보 부족 시 제약 완화:
1. **First Try**: `shownAssetIds` 제외
2. **Fallback**: `shownAssetIds` 제약 완화
3. **Last Resort**: 다양성 조건 완화 (경고 로그)

## Round 10 완료 시 자동 처리

1. 포트폴리오에 10개 자산 추가 (균등 비중 10%)
2. 포트폴리오 스냅샷 생성
3. **포트폴리오 자동 시작** (DRAFT → ACTIVE)
4. **메인 포트폴리오 자동 설정** (없을 경우)

## Example Flow

```
Round 3:
  preferredSectors = [INFORMATION_TECHNOLOGY, HEALTH_CARE]
  riskProfile = BALANCED

1. Load preferredCandidates (IT + Healthcare, ~80개)
2. Load wildCandidates (~40개)
3. Pick 2 normal (섹터 선호 1.5x, 리스크 가중치)
4. Pick 1 wild (섹터 무시, 의외성)
5. Check diversity (2+ sectors, 2+ risk bands)
6. Return 3 assets

Result:
  - AAPL (IT, risk=4) - normal pick
  - JNJ (Healthcare, risk=3) - normal pick
  - XOM (Energy, risk=4) - wild pick
```