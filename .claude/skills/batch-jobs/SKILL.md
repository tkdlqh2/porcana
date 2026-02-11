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

**03:00 KST (매일) - 삭제된 포트폴리오 정리**
```bash
# 30일 이상 경과한 삭제 포트폴리오 하드 삭제
./gradlew bootRun --args='--spring.batch.job.names=deletedPortfolioCleanupJob'
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

**OHLC 데이터 백필 (일회성 수동 실행)**
```bash
# application.yml에서 enabled=true 설정 후 실행
# 또는 환경변수로 활성화
OHLC_BACKFILL_ENABLED=true ./gradlew bootRun

# 특정 날짜 이후의 AssetPrice 데이터를 삭제하고 OHLC 형식으로 재수집
```

## 삭제된 포트폴리오 정리 (Deleted Portfolio Cleanup)

### 목적
- Soft delete된 포트폴리오 중 30일 이상 경과한 것을 하드 삭제
- 관련된 모든 데이터(자산, 스냅샷, 수익률, 아레나 세션 등) 함께 삭제

### 삭제 순서 (FK 제약 조건 준수)
```
1. ArenaRoundChoices → ArenaRound
2. ArenaRound → ArenaSession
3. SnapshotAssetDailyReturn
4. PortfolioDailyReturn (FK: snapshot_id)
5. PortfolioSnapshotAsset → PortfolioSnapshot
6. PortfolioAsset
7. Portfolio (최종 삭제)
```

### 설정
```java
// DeletedPortfolioCleanupBatchJob.java
private static final int RETENTION_DAYS = 30;  // 보관 기간

// 30일 이상 경과한 삭제 포트폴리오 조회
LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);
List<Portfolio> portfoliosToDelete = portfolioRepository.findDeletedPortfoliosOlderThan(cutoffDate);
```

### 로그 예시
```
Starting deleted portfolio cleanup (retention: 30 days)
Cutoff date for cleanup: 2024-01-15T03:00:00
Found 5 portfolios to hard-delete
Hard-deleted portfolio: uuid-123 (deleted at: 2023-12-10T10:30:00)
Hard-deleted portfolio: uuid-456 (deleted at: 2023-12-12T15:45:00)
...
Deleted portfolio cleanup completed: 5 portfolios deleted
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

**2. 금액 기반 비중 계산 (초기 투자금 10,000,000원 가정)**
```java
// 초기 가상 투자금
private static final BigDecimal INITIAL_INVESTMENT_KRW = new BigDecimal("10000000.00");

// First pass: 자산별 현재 평가금액 계산 (KRW)
for (각 자산) {
    초기비중 = snapshotAsset.getWeight();  // 예: 10.0%
    초기금액 = 10,000,000 × 0.10 = 1,000,000원

    수익률 = calculateAssetReturn(...);
    현재평가금액 = 초기금액 × (1 + 수익률/100);  // valueKrw

    전체평가금액 += 현재평가금액;
}

// Second pass: 비중 자동 계산 및 저장
for (각 자산) {
    현재비중 = (valueKrw / totalValueKrw) × 100;  // weightUsed
    // SnapshotAssetDailyReturn에 weightUsed, valueKrw 저장
}

// PortfolioDailyReturn에 totalValueKrw 저장
```

**예시:**
- 삼성전자: 초기 10%(1,000,000원), 수익률 +20% → valueKrw 1,200,000원 → 비중 약 10.9%
- 카카오: 초기 10%(1,000,000원), 수익률 -10% → valueKrw 900,000원 → 비중 약 9.1%
- 전체: totalValueKrw = 11,000,000원

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
- `total_value_krw`: **포트폴리오 전체 평가금액 (원화)** 💰

**SnapshotAssetDailyReturn (자산별)**
- `weight_used`: **금액 기반 동적 비중** (%)
- `value_krw`: **자산 평가금액 (원화)** 💰
- `asset_return_local`: 자산 로컬 수익률 (%)
- `asset_return_total`: 자산 전체 수익률 (%)
- `fx_return`: 환율 수익률 (%)
- `contribution_total`: 포트폴리오 수익률 기여도 (%)

### 중요 포인트

**금액 기반 계산 (가장 중요):**
- 초기 투자금: **10,000,000원** 가정
- `valueKrw`: 자산 평가금액 (원화) - 실제 금액
- `totalValueKrw`: 포트폴리오 전체 평가금액 (원화)
- `weightUsed`: 금액 기반으로 자동 계산된 동적 비중 (%)

**weightUsed 계산 변화:**
- ❌ **이전**: 스냅샷의 고정 비중을 그대로 복사 (시간이 지나도 변하지 않음)
- ✅ **현재**: 금액 기반 동적 비중 (가격 변동에 따라 자동 조정)

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

## OHLC 데이터 백필 (OhlcDataBackfillRunner)

### 목적
특정 날짜 이후의 모든 종목 가격 데이터를 OHLC(Open-High-Low-Close) 형식으로 재수집

### 사용법
```bash
# 방법 1: 환경변수 사용 (권장)
OHLC_BACKFILL_ENABLED=true ./gradlew bootRun

# 방법 2: application.yml 수정
# batch.runner.ohlc-backfill.enabled: true 설정 후
./gradlew bootRun
```

### 설정
```yaml
# application.yml
batch:
  runner:
    ohlc-backfill:
      enabled: false  # 기본값: false (비활성화)
```

### 처리 흐름
```
1. 특정 날짜(BACKFILL_START_DATE) 이후의 AssetPrice 데이터 삭제
   ├─ 별도 트랜잭션으로 즉시 커밋
   └─ 삭제 완료 후 재수집 시작

2. 모든 active=true 자산에 대해:
   ├─ DataProvider를 통해 OHLC 데이터 수집
   └─ AssetPrice에 저장 (openPrice, highPrice, lowPrice, closePrice)

3. Rate Limiting
   ├─ 한국 API: 100ms 딜레이
   └─ 미국 API: 150ms 딜레이
```

### 트랜잭션 처리 (Self-Injection Pattern)

**문제점:**
- 같은 클래스 내에서 `this.method()`로 호출 시 Spring AOP Proxy가 작동하지 않음
- @Transactional이 적용되지 않아 트랜잭션이 분리되지 않음

**해결책: Self-Injection Pattern**
```java
@Component
public class OhlcDataBackfillRunner implements ApplicationRunner {

    private OhlcDataBackfillRunner self;

    @Autowired
    public void setSelf(@Lazy OhlcDataBackfillRunner self) {
        this.self = self;  // Proxy 객체 주입
    }

    @Override
    public void run(ApplicationArguments args) {
        // self를 통해 호출 → Proxy를 거쳐 @Transactional 적용
        self.deleteExistingData();  // 별도 트랜잭션, 즉시 커밋
        backfillOhlcData();         // 재수집
    }

    @Transactional
    protected void deleteExistingData() {
        assetPriceRepository.deleteByPriceDateGreaterThanEqual(BACKFILL_START_DATE);
        log.info("Deleted existing AssetPrice data from {}", BACKFILL_START_DATE);
    }

    @Transactional
    protected void saveAssetPrices(List<AssetPrice> prices) {
        assetPriceRepository.saveAll(prices);
    }
}
```

**핵심 포인트:**
- `@Lazy` 사용: 순환 의존성 방지
- `self.method()` 호출: Spring Proxy를 통해 AOP 적용
- 삭제와 삽입을 **별도 트랜잭션**으로 분리
- 삭제 트랜잭션이 즉시 커밋되어 DB 반영 후 재수집 시작

## Discord Webhook 알림 시스템

### 목적
배치 작업의 성공/실패/경고 상태를 Discord 채널로 실시간 알림

### 설정
```yaml
# application.yml
notification:
  discord:
    enabled: ${DISCORD_NOTIFICATION_ENABLED:false}
    webhook-url: ${DISCORD_WEBHOOK_URL:}
```

```bash
# 환경변수 설정 (권장)
export DISCORD_NOTIFICATION_ENABLED=true
export DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/your-webhook-url
```

### 자동 적용

**BatchNotificationListener**가 모든 배치 작업을 자동 모니터링:

```java
@Component
public class BatchNotificationListener implements JobExecutionListener {

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (status == BatchStatus.COMPLETED) {
            // ✅ 성공 알림 (녹색)
            discordNotificationService.sendBatchSuccess(jobName, durationMs, summary);
        } else if (status == BatchStatus.FAILED) {
            // ❌ 실패 알림 (빨간색)
            discordNotificationService.sendBatchFailure(jobName, durationMs, errorMessage);
        } else {
            // ⚠️ 경고 알림 (주황색)
            discordNotificationService.sendBatchWarning(jobName, message);
        }
    }
}
```

### 배치 작업에 적용 방법

각 배치 Job에 리스너 추가:

```java
@Configuration
@RequiredArgsConstructor
public class YourBatchJob {

    private final BatchNotificationListener batchNotificationListener;

    @Bean
    public Job yourJob() {
        return new JobBuilder("yourJobName", jobRepository)
                .listener(batchNotificationListener)  // 추가
                .start(yourStep())
                .build();
    }
}
```

### 알림 형식

**성공 알림 (녹색)**
```
✅ Batch Job Success
krAssetJob completed successfully

Duration: 2m 35s
Time: 2024-01-15 14:30:00
Summary:
**fetchKrAssetsStep**
- Read: 500
- Write: 500
- Skip: 0
- Commit: 10
```

**실패 알림 (빨간색)**
```
❌ Batch Job Failed
krAssetJob failed

Duration: 1m 20s
Time: 2024-01-15 14:30:00
Error:
**DataIntegrityViolationException**
```
Duplicate key value violates unique constraint
```

**Failed Step:** fetchKrAssetsStep
- Read: 250
- Write: 200
- Skip: 0
- Error: Constraint violation
```

> 📖 **상세 설정 가이드**: 프로젝트 루트의 `DISCORD_NOTIFICATION_GUIDE.md` 참조

## 공통 처리 원칙

- **Upsert 전략**: symbol + market을 natural key로 사용
- **as_of 관리**: 배치 실행일을 기록
- **active 플래그**: 유니버스 포함 종목만 true
- **이력 관리**: as_of 기준으로 과거 데이터 조회 가능
- **동적 비중**: weightUsed는 시가총액 기반으로 매일 자동 조정
- **트랜잭션 분리**: Self-Injection Pattern으로 별도 트랜잭션 보장
- **모니터링**: Discord Webhook으로 모든 배치 작업 자동 알림