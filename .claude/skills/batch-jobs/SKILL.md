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

## 특수 배치 Job

**US 이미지 업데이트 (수동 실행)**
```bash
# 미국 주식 이미지 URL 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usImageUpdateJob'
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

## 공통 처리 원칙

- **Upsert 전략**: symbol + market을 natural key로 사용
- **as_of 관리**: 배치 실행일을 기록
- **active 플래그**: 유니버스 포함 종목만 true
- **이력 관리**: as_of 기준으로 과거 데이터 조회 가능