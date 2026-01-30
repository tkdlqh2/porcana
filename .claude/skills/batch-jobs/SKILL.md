---
name: batch-jobs
description: Porcana Spring Batch jobs reference. Use when working with batch jobs or scheduling.
disable-model-invocation: false
---

# Porcana Batch Jobs Reference

## Batch Job 목록

### 주간 스케줄 (일요일)

**02:00 KST - 종목 데이터 업데이트**
```bash
# 한국 주식 종목 업데이트
./gradlew bootRun --args='--spring.batch.job.names=krAssetBatchJob'

# 한국 ETF 종목 업데이트 + 과거 가격
./gradlew bootRun --args='--spring.batch.job.names=krEtfJob'

# 미국 주식 종목 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usAssetBatchJob'

# 미국 ETF 종목 업데이트 + 과거 가격
./gradlew bootRun --args='--spring.batch.job.names=usEtfJob'
```

**03:00 KST - 위험도 계산**
```bash
# 종목 위험도 계산 및 업데이트
./gradlew bootRun --args='--spring.batch.job.names=assetRiskJob'
```

### 일일 스케줄 (평일)

**07:00 KST (화-토) - 미국 시장 가격 업데이트**
```bash
# 미국 주식 가격
./gradlew bootRun --args='--spring.batch.job.names=usDailyPriceJob'

# 미국 ETF 가격
./gradlew bootRun --args='--spring.batch.job.names=usEtfDailyPriceJob'
```

**12:00 KST (월-금) - 환율 업데이트**
```bash
# 환율 데이터
./gradlew bootRun --args='--spring.batch.job.names=exchangeRateJob'
```

**18:00 KST (월-금) - 한국 시장 가격 업데이트**
```bash
# 한국 주식 가격
./gradlew bootRun --args='--spring.batch.job.names=krDailyPriceJob'

# 한국 ETF 가격
./gradlew bootRun --args='--spring.batch.job.names=krEtfDailyPriceJob'
```

**19:00 KST (매일) - 포트폴리오 수익률 계산**
```bash
# 포트폴리오 일별 수익률 계산
./gradlew bootRun --args='--spring.batch.job.names=portfolioPerformanceJob'
```

## 특수 배치 Job

**US 이미지 업데이트 (수동 실행)**
```bash
# 미국 주식 이미지 URL 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usImageUpdateJob'
```

**포트폴리오 비중 재계산 (일회성 수동 실행)**
```bash
# application.yml에서 enabled=true 설정 후 실행
# 또는 환경변수로 활성화
RECALCULATE_WEIGHT_USED_ENABLED=true ./gradlew bootRun

# 기존 SnapshotAssetDailyReturn의 weightUsed를 시가총액 기반으로 재계산
```

## Batch Job 구조 패턴

### 한국 종목 배치
```
KrAssetBatchJob
├─ Step 1: fetchKrAssetsStep
│   └─ CSV에서 종목 코드 읽기 → API 호출 → upsert
├─ Step 2: tagKospi200Step
│   └─ kospi200.csv 기반 태깅 및 활성화
└─ Step 3: tagKosdaq150Step
    └─ kosdaq150.csv 기반 태깅 및 활성화
```

### 미국 종목 배치
```
UsAssetBatchJob
└─ Step 1: FMP API 호출 → Upsert (market=US, active=true)
```

### 일일 가격 업데이트
- **Rate Limiting**:
  - 한국 API: 100ms 딜레이
  - 미국 API: 150ms 딜레이
- **중복 체크**: `(asset_id, price_date)` unique constraint
- **데이터 범위**: 최근 3-5일치 조회하여 최신 거래일 확보

## 위험도 계산 로직

### 핵심 지표
1. **변동성 (Volatility)** - 최근 60 거래일
2. **최대낙폭 (MDD)** - 최근 252 거래일
3. **1일 최악 하락** - 최근 252 거래일

### RiskScore 계산
```
riskScore = 100 × (0.45 × volPct + 0.45 × mddPct + 0.10 × worstPct)
```

### RiskLevel 매핑 (퀸타일)
- 0~20 → 1 (Low)
- 20~40 → 2
- 40~60 → 3
- 60~80 → 4
- 80~100 → 5 (High)

## Batch 스케줄링 설정

```java
// 한국 시장: 평일 18:00 KST
@Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
public void runKrDailyPriceUpdate()

// 미국 시장: 평일 07:00 KST (화-토, 시차 고려)
@Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
public void runUsDailyPriceUpdate()
```

## 포트폴리오 수익률 계산 (Portfolio Performance Batch)

### 목적
- ACTIVE 상태 포트폴리오의 일별 수익률 계산
- 시가총액 기반 동적 비중 계산 및 저장
- 환율 효과 분리 추적 (로컬 수익률 vs 환율 수익률)

### 계산 로직

**1. 적용 스냅샷 찾기**
```java
// effectiveDate <= targetDate 중 가장 최근 스냅샷 사용
PortfolioSnapshot snapshot = findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
    portfolioId, targetDate
);
```

**2. 시가총액 기반 비중 계산**
```java
// First pass: 현재 평가금액 계산
for (각 자산) {
    초기비중 = snapshotAsset.getWeight();  // 예: 10.0%
    현재평가금액 = 초기비중 × (1 + 수익률/100);
    전체평가금액 += 현재평가금액;
}

// Second pass: 정규화된 비중 계산
for (각 자산) {
    현재비중 = (현재평가금액 / 전체평가금액) × 100;
    // SnapshotAssetDailyReturn.weightUsed에 저장
}
```

**예시:**
- 삼성전자: 초기 10%, 수익률 +20% → 현재 평가 12 → 현재 비중 약 11%
- 카카오: 초기 10%, 수익률 -10% → 현재 평가 9 → 현재 비중 약 9%

**3. 환율 효과 분리**
```java
// 미국 자산의 경우
assetReturnTotal = assetReturnLocal + fxReturn

// 한국 자산의 경우
fxReturn = 0
assetReturnTotal = assetReturnLocal
```

### 저장 데이터

**PortfolioDailyReturn (포트폴리오 전체)**
- `return_total`: 전체 수익률 (%)
- `return_local`: 로컬 가격 변동 수익률 (%)
- `return_fx`: 환율 변동 수익률 (%)

**SnapshotAssetDailyReturn (자산별)**
- `weight_used`: **시가총액 기반 동적 비중** (%)
- `asset_return_local`: 자산 로컬 수익률 (%)
- `asset_return_total`: 자산 전체 수익률 (%)
- `fx_return`: 환율 수익률 (%)
- `contribution_total`: 포트폴리오 수익률 기여도 (%)

### 중요 포인트

**weightUsed의 의미:**
- ❌ **이전**: 스냅샷의 고정 비중을 그대로 복사 (시간이 지나도 변하지 않음)
- ✅ **현재**: 시가총액 기반 동적 비중 (가격 변동에 따라 자동 조정)

**API에서 사용:**
```java
// HomeService, PortfolioService
// 최신 weightUsed를 조회하여 현재 비중 표시
Optional<SnapshotAssetDailyReturn> latest =
    findFirstByPortfolioIdAndAssetIdOrderByReturnDateDesc(portfolioId, assetId);
Double currentWeight = latest.get().getWeightUsed();
```

## 포트폴리오 비중 재계산 (Recalculate Weight Used)

### 목적
기존에 잘못 계산된 `weightUsed` 데이터를 시가총액 기반으로 재계산

### 사용법
```bash
# 방법 1: 환경변수 사용 (권장)
RECALCULATE_WEIGHT_USED_ENABLED=true ./gradlew bootRun

# 방법 2: application.yml 수정
# batch.runner.recalculate-weight-used.enabled: true 설정 후
./gradlew bootRun
```

### 설정
```yaml
# application.yml
batch:
  runner:
    recalculate-weight-used:
      enabled: false  # 기본값: false (비활성화)
```

### 처리 흐름
```
1. 모든 포트폴리오 조회
2. 각 포트폴리오에 대해:
   ├─ 모든 SnapshotAssetDailyReturn 조회 (날짜순)
   ├─ 날짜별로 그룹핑
   └─ 각 날짜에 대해 시가총액 기반 비중 재계산
3. Reflection을 사용하여 weightUsed 필드 업데이트
```

### 로그 예시
```
[1/15] Processing portfolio: My Portfolio (uuid-123)
  ✓ Recalculated 45 daily returns
[2/15] Processing portfolio: Test Portfolio (uuid-456)
  ✓ Recalculated 30 daily returns
...
========================================
WeightUsed Recalculation completed
Total portfolios: 15
Successfully processed: 15
Failed: 0
Total daily returns recalculated: 675
========================================
```

## 공통 처리 원칙

- **Upsert 전략**: symbol + market을 natural key로 사용
- **as_of 관리**: 배치 실행일을 기록
- **active 플래그**: 유니버스 포함 종목만 true
- **이력 관리**: as_of 기준으로 과거 데이터 조회 가능
- **동적 비중**: weightUsed는 시가총액 기반으로 매일 자동 조정