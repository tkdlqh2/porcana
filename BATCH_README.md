# Asset Batch Jobs

Spring Batch 기반 종목 데이터 수집 및 관리 시스템

## 개요

Porcana는 한국 시장(KR)과 미국 시장(US)의 종목 데이터를 외부 API로부터 수집하여 Asset 테이블에 저장합니다.
배치 작업은 Provider 추상화 레이어를 통해 API 소스를 쉽게 교체할 수 있도록 설계되었습니다.

## 아키텍처

```
batch/
├── provider/              # 외부 API 추상화 레이어
│   ├── AssetDataProvider.java (인터페이스)
│   ├── kr/
│   │   ├── KrAssetDataProvider.java
│   │   ├── DataGoKrAssetProvider.java (data.go.kr 구현체)
│   │   └── UniverseTaggingProvider.java (CSV 기반 태깅)
│   └── us/
│       ├── UsAssetDataProvider.java
│       └── FmpAssetProvider.java (FMP API 구현체)
├── dto/
│   └── AssetBatchDto.java
├── job/
│   ├── KrAssetBatchJob.java
│   └── UsAssetBatchJob.java
└── config/
    ├── BatchConfig.java
    └── BatchRestTemplateConfig.java
```

### 추상화 레이어의 장점

1. **API Provider 교체 용이**: 인터페이스만 구현하면 다른 API로 쉽게 전환 가능
2. **시장별 독립성**: KR/US 시장별로 독립적인 Provider 관리
3. **테스트 용이성**: Mock Provider로 쉽게 단위 테스트 작성 가능

## 배치 작업

### 1. 한국 시장 배치 (krAssetJob)

**데이터 소스:**
- data.go.kr 공공데이터포털 API
- KOSPI200 구성종목 CSV
- KOSDAQ150 구성종목 CSV

**처리 단계:**
1. **fetchKrAssetsStep**: data.go.kr API에서 전체 상장종목 조회
2. **tagKospi200Step**: KOSPI200 구성종목 태깅 및 활성화
3. **tagKosdaq150Step**: KOSDAQ150 구성종목 태깅 및 활성화

**실행 방법:**
```bash
# Gradle로 실행
./gradlew bootRun --args='--spring.batch.job.names=krAssetJob'

# JAR로 실행
java -jar porcana.jar --spring.batch.job.names=krAssetJob
```

### 2. 미국 시장 배치 (usAssetJob)

**데이터 소스:**
- FMP (Financial Modeling Prep) API - S&P 500 Constituents

**처리 단계:**
1. **fetchUsAssetsStep**: FMP API에서 S&P 500 구성종목 조회 및 저장

**실행 방법:**
```bash
# Gradle로 실행
./gradlew bootRun --args='--spring.batch.job.names=usAssetJob'

# JAR로 실행
java -jar porcana.jar --spring.batch.job.names=usAssetJob
```

### 3. 모든 배치 실행

```bash
./gradlew bootRun --args='--spring.batch.job.names=krAssetJob,usAssetJob'
```

## 설정

### API 키 설정

환경 변수로 API 키를 설정하세요:

```bash
# 한국 공공데이터포털 API 키
export DATAGOKR_API_KEY=your_api_key_here

# FMP API 키
export FMP_API_KEY=your_fmp_api_key_here
```

또는 `application.yml`에서 직접 설정:

```yaml
batch:
  provider:
    kr:
      api-key: your_api_key_here
    us:
      api-key: your_fmp_api_key_here
```

### CSV 파일 관리

Universe 태깅용 CSV 파일 위치:
- `src/main/resources/batch/kospi200.csv`
- `src/main/resources/batch/kosdaq150.csv`

**CSV 형식:**
```csv
# 주석은 # 으로 시작
# 한 줄에 하나의 종목 코드
005930
000660
035420
```

## Upsert 전략

- **Natural Key**: `symbol` + `market` 조합으로 중복 확인
- 기존 데이터가 있으면 업데이트, 없으면 신규 생성
- `asOf` 필드로 데이터 기준일 관리

## API Provider 교체 방법

### 예시: 한국 시장 Provider를 다른 API로 변경

1. **새 Provider 구현:**
```java
@Component
public class NewKrAssetProvider implements KrAssetDataProvider {
    @Override
    public List<AssetBatchDto> fetchAssets() throws AssetDataProviderException {
        // 새로운 API 호출 로직
    }

    @Override
    public String getProviderName() {
        return "NEW_KR_PROVIDER";
    }
}
```

2. **Bean 우선순위 설정:**
```java
@Primary  // 이 Provider를 기본으로 사용
@Component
public class NewKrAssetProvider implements KrAssetDataProvider {
    // ...
}
```

3. **기존 Provider 비활성화:**
```java
@Component
@ConditionalOnProperty(name = "batch.provider.kr.type", havingValue = "datagokr")
public class DataGoKrAssetProvider implements KrAssetDataProvider {
    // ...
}
```

## 모니터링

배치 실행 로그 확인:
```bash
# 배치 실행 로그 레벨
logging:
  level:
    org.springframework.batch: DEBUG
    com.porcana.batch: DEBUG
```

## 스케줄링

자동 배치 실행 스케줄러가 `BatchConfig`에 구현되어 있습니다.

**실행 주기:**
- 매주 일요일 새벽 2시 (KST, Asia/Seoul 타임존)
- 한국 시장(krAssetJob)과 미국 시장(usAssetJob)을 순차 실행

**구현 코드:** `com.porcana.batch.config.BatchConfig`

```java
@Scheduled(cron = "0 0 2 * * SUN", zone = "Asia/Seoul")
public void runWeeklyAssetUpdate() {
    // KR 시장 배치 실행
    jobLauncher.run(krAssetJob, krParams);

    // US 시장 배치 실행
    jobLauncher.run(usAssetJob, usParams);
}
```

**스케줄러 비활성화:**
스케줄러를 비활성화하려면 `application.yml`에 다음 설정 추가:
```yaml
spring:
  task:
    scheduling:
      enabled: false
```

**스케줄 변경:**
`BatchConfig.java`의 `@Scheduled` cron 표현식을 수정하세요.
- 예: 매일 새벽 3시 → `"0 0 3 * * *"`
- 예: 매주 월요일 새벽 1시 → `"0 0 1 * * MON"`

## 트러블슈팅

### API 키가 설정되지 않은 경우

Provider는 API 키가 없으면 빈 리스트를 반환하고 경고 로그를 출력합니다:
```
WARN - FMP API key not configured. Skipping S&P 500 fetch.
```

### CSV 파일이 없는 경우

CSV 파일이 없으면 해당 태깅 단계를 건너뜁니다:
```
WARN - CSV file not found: batch/kospi200.csv. Skipping KOSPI200 tagging.
```

### 배치 실행 실패

1. 로그 확인: `logging.level.org.springframework.batch=DEBUG`
2. 데이터베이스 연결 확인
3. API 키 유효성 확인
4. 네트워크 연결 확인

## 데이터베이스

Spring Batch는 자체 메타데이터 테이블을 생성합니다:
- `BATCH_JOB_INSTANCE`
- `BATCH_JOB_EXECUTION`
- `BATCH_STEP_EXECUTION`
- 등등

이 테이블들을 통해 배치 실행 이력을 조회할 수 있습니다.