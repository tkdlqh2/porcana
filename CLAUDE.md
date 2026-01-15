# Porcana MVP API Contract (Frontend 공유용)

## Development Philosophy

### Request DTO as Record
- **Request DTO**: Java Record로 작성 (불변성, 간결함)
- **Response DTO**: @Builder + Lombok 사용 (유연한 생성)
- **Command DTO**: @Builder + Lombok 사용 (내부 로직용)

**Request DTO Example:**
```java
public record SignupRequest(
    @Email(message = "올바른 이메일 형식이 아닙니다")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    String password,

    @NotBlank(message = "닉네임은 필수입니다")
    String nickname
) {
}
```

**이유:**
- Record는 불변 객체로 API 요청 데이터에 적합
- Validation annotation을 필드에 직접 적용 가능
- getter 메서드 자동 생성 (field명과 동일: `request.email()`)
- equals/hashCode/toString 자동 생성

### Command Pattern
- **Controller**: Request DTO 수신 → Command 생성 (Command.from(request)) → Service 호출
- **Command**: Request로부터 자신을 생성하는 정적 팩토리 메서드 제공
- **Service**: Command DTO 수신 → 비즈니스 로직 처리
- **Entity**: Command로부터 생성 (Entity.from(command))

**Flow Example:**
```
Request DTO (SignupRequest)
    ↓ Command의 정적 팩토리 메서드
Command DTO (SignupCommand.from(request))
    ↓ Service로 전달
Entity (User.from(command))
```

**Code Example:**
```java
// Controller
@PostMapping("/signup")
public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
    SignupCommand command = SignupCommand.from(request);
    AuthResponse response = authService.signup(command);
    return ResponseEntity.ok(response);
}

// Command
public static SignupCommand from(SignupRequest request) {
    return SignupCommand.builder()
        .email(request.email())
        .password(request.password())
        .nickname(request.mickname())
        .provider(User.AuthProvider.EMAIL)
        .build();
}
```

**이유:**
- 계층 간 명확한 책임 분리
- Request DTO는 API 스펙에 종속, Command는 도메인 로직에 집중
- **변환 로직을 Command가 소유** → Controller는 단순히 호출만
- Entity 생성 로직을 Entity 내부에 캡슐화

### Swagger Authentication (JWT)
- **Bearer Token 방식**: Swagger UI에서 JWT 토큰 인증 지원
- **Annotation 기반 구분**: @SecurityRequirement로 인증 필요 API 표시
- **SecurityScheme 설정**: SwaggerConfig에서 JWT scheme 등록

**인증이 필요한 API:**
```java
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/api/v1")
public class UserController {
    // 이 컨트롤러의 모든 엔드포인트에 JWT 필요
    // Swagger UI에서 자물쇠 아이콘 표시됨
}
```

**인증이 필요없는 API:**
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // @SecurityRequirement 없음 = 공개 API
    // Swagger UI에서 자물쇠 아이콘 없음
}
```

**Custom Annotation - @CurrentUser:**
```java
@GetMapping("/me")
public ResponseEntity<UserResponse> getMe(@CurrentUser UUID userId) {
    // @CurrentUser: JWT 토큰에서 userId 자동 추출
    // CurrentUserArgumentResolver가 SecurityContext에서 userId 파싱
}
```

**이유:**
- API별 인증 요구사항을 명확히 구분
- Swagger UI에서 시각적으로 인증 필요 여부 확인 가능
- @CurrentUser로 반복적인 인증 처리 코드 제거

---

## Data Model & Batch Strategy

### Asset (종목) Entity

종목 데이터를 Record 형태로 관리하는 불변 테이블입니다.

**필드 설계:**
- `id`: UUID (Primary Key)
- `market`: ENUM (KR | US) - 시장 구분
- `symbol`: String - 종목 코드 (US: AAPL, KR: 005930)
- `name`: String - 종목명
- `type`: ENUM (STOCK | ETF) - 상품 유형
- `sector`: ENUM (Sector) - GICS 표준 섹터 (주식 전용, ETF는 NULL)
- `asset_class`: ENUM (AssetClass) - ETF 자산 클래스 (ETF 전용, STOCK은 NULL)
- `universe_tags`: List<UniverseTag> - 유니버스 태그 (SP500, NASDAQ100, KOSPI200, KOSDAQ150 등)
- `current_risk_level`: Integer (1~5) - 현재 위험도 (1: Low, 5: High)
- `active`: Boolean - 활성화 여부 (카드 풀에 포함 여부)
- `as_of`: LocalDate - 기준일 (이 레코드가 "언제 기준"인지)
- `created_at`: LocalDateTime
- `updated_at`: LocalDateTime

**Entity Example:**
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
    private Sector sector;  // GICS 표준 섹터 (주식 전용)

    @Enumerated(EnumType.STRING)
    private AssetClass assetClass;  // ETF 자산 클래스 (ETF 전용)

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<UniverseTag> universeTags;  // [SP500, NASDAQ100]

    private Boolean active;
    private LocalDate asOf;

    public enum Market { KR, US }
    public enum AssetType { STOCK, ETF }
}
```

**Sector ENUM (GICS 표준):**
```java
public enum Sector {
    MATERIALS,                  // 소재
    COMMUNICATION_SERVICES,     // 커뮤니케이션 서비스
    CONSUMER_DISCRETIONARY,     // 임의소비재
    CONSUMER_STAPLES,          // 필수소비재
    ENERGY,                    // 에너지
    FINANCIALS,                // 금융
    HEALTH_CARE,               // 헬스케어
    INDUSTRIALS,               // 산업재
    REAL_ESTATE,               // 부동산
    INFORMATION_TECHNOLOGY,    // 정보기술
    UTILITIES                  // 유틸리티
}
```

**FMP API Sector → GICS Sector 매핑:**
- Basic Materials → MATERIALS
- Communication Services → COMMUNICATION_SERVICES
- Consumer Cyclical → CONSUMER_DISCRETIONARY
- Consumer Defensive → CONSUMER_STAPLES
- Energy → ENERGY
- Financial Services → FINANCIALS
- Healthcare → HEALTH_CARE
- Industrials → INDUSTRIALS
- Real Estate → REAL_ESTATE
- Technology → INFORMATION_TECHNOLOGY
- Utilities → UTILITIES

**AssetClass ENUM (ETF 분류):**
```java
public enum AssetClass {
    EQUITY_INDEX,    // 주식 인덱스 ETF (SPY, KODEX 200)
    SECTOR,          // 섹터별 ETF (XLK, KODEX 반도체)
    DIVIDEND,        // 배당 ETF (SCHD, TIGER 고배당)
    BOND,            // 채권 ETF (TLT, KODEX 국고채10년)
    COMMODITY        // 원자재 ETF (GLD, KRX 금현물)
}
```

**주의사항:**
- `sector`는 주식(STOCK) 전용 필드로, ETF는 NULL
- `assetClass`는 ETF 전용 필드로, 주식은 NULL

### Spring Batch 기반 종목 데이터 생성 전략

**한국 종목 (KR Market):**
1. **종목 데이터 수집** (data.go.kr API)
   - CSV 파일(kospi200.csv, kosdaq150.csv)에서 종목 코드 목록 읽기 (약 348개)
   - 각 종목 코드마다 data.go.kr의 `getStockPriceInfo` API를 개별 호출
   - 응답 데이터를 `assets` 테이블에 upsert (초기 active=false)
   - 기본 정보: symbol, name, exchange (KOSPI/KOSDAQ), type (STOCK/ETF)
   - 주의: 약 348개 종목 × API 호출이므로 시간 소요 (약 5-10분)

2. **유니버스 태깅**
   - `kospi200.csv`: KOSPI200 구성종목 코드 목록 (약 199개)
   - `kosdaq150.csv`: KOSDAQ150 구성종목 코드 목록 (약 149개)
   - CSV의 종목 코드를 기준으로 `universe_tags` 추가
   - 태깅된 종목만 `active = true` 설정 → 카드 풀에 포함

3. **Batch Job 구조**
   ```
   KrAssetBatchJob
   ├─ Step 1: fetchKrAssetsStep
   │   └─ CSV에서 종목 코드 읽기 → 각 코드마다 API 호출 → assets 테이블 upsert
   ├─ Step 2: tagKospi200Step
   │   └─ kospi200.csv 기반 태깅 및 활성화
   └─ Step 3: tagKosdaq150Step
       └─ kosdaq150.csv 기반 태깅 및 활성화
   ```

**미국 종목 (US Market):**
1. **S&P 500 구성종목 수집** (FMP API)
   - FMP (Financial Modeling Prep) Constituents API 사용
   - Endpoint: `/api/v3/sp500_constituent`
   - 응답: symbol, name, sector, subSector 등

2. **데이터 처리**
   - exchange는 FMP 응답 또는 별도 조회로 확인 (NASDAQ/NYSE)
   - `universe_tags = ["SP500"]`
   - `active = true` 설정
   - `type = STOCK` (ETF는 별도 처리)

3. **Batch Job 구조**
   ```
   UsAssetBatchJob
   └─ Step 1: Fetch from FMP → Upsert to assets (market=US, active=true)
   ```

**공통 처리 원칙:**
- **Upsert 전략**: symbol + market을 natural key로 사용, 중복 시 업데이트
- **as_of 관리**: 배치 실행일을 `as_of`로 기록 (데이터의 시점 추적)
- **active 플래그**: 유니버스에 포함된 종목만 `active=true`, 나머지는 `false`
- **이력 관리**: 필요 시 `as_of` 기준으로 과거 구성종목 조회 가능 (향후 확장)

**배치 실행 주기:**
- 초기 데이터 구축: 수동 실행 (./gradlew bootRun --args='--spring.batch.job.names=krAssetBatchJob')
- 정기 업데이트: 주 1회 스케줄링 (매주 일요일 02:00 KST)

### ETF 데이터 생성 전략

**한국 ETF (KR Market):**
1. **ETF 데이터 수집** (CSV 기반)
   - CSV 파일 (`kr_etf.csv`)에서 ETF 목록 읽기
   - 포맷: `symbol, name, asset_class`
   - 예시: `069500, KODEX 200, EQUITY_INDEX`

2. **데이터 처리**
   - `type = ETF`
   - `active = true` (CSV에 있는 ETF는 모두 활성화)
   - `assetClass` 매핑: CSV의 asset_class 컬럼 값

3. **Batch Job 구조**
   ```
   KrEtfBatchJob
   ├─ Step 1: importKrEtfsStep
   │   └─ kr_etf.csv 읽기 → assets 테이블 upsert
   └─ Step 2: fetchKrEtfHistoricalPricesStep
       └─ 과거 1년치 가격 데이터 수집 (24시간 이내 생성된 ETF만)
   ```

4. **가격 데이터 소스**
   - API: data.go.kr `GetSecuritiesProductInfoService/getETFPriceInfo`
   - 과거 1년치 일일 가격 데이터 수집

**미국 ETF (US Market):**
1. **ETF 데이터 수집** (CSV 기반)
   - CSV 파일 (`us_etf.csv`)에서 ETF 목록 읽기
   - 포맷: `symbol, name, asset_class`
   - 예시: `SPY, S&P 500 ETF, EQUITY_INDEX`

2. **데이터 처리**
   - `type = ETF`
   - `active = true`
   - `assetClass` 매핑: CSV의 asset_class 컬럼 값

3. **Batch Job 구조**
   ```
   UsEtfBatchJob
   ├─ Step 1: importUsEtfsStep
   │   └─ us_etf.csv 읽기 → assets 테이블 upsert
   └─ Step 2: fetchUsEtfHistoricalPricesStep
       └─ 과거 1년치 가격 데이터 수집 (24시간 이내 생성된 ETF만)
   ```

4. **가격 데이터 소스**
   - API: FMP `historical-price-eod`
   - 주식과 동일한 API 사용

**배치 실행 주기:**
- 초기 데이터 구축: 수동 실행
  ```bash
  ./gradlew bootRun --args='--spring.batch.job.names=krEtfJob'
  ./gradlew bootRun --args='--spring.batch.job.names=usEtfJob'
  ```
- 정기 업데이트: 주 1회 스케줄링 (매주 일요일 02:00 KST, 주식과 함께)

---

## Spring Batch 기반 일일 가격 업데이트 전략

### AssetPrice (가격 데이터) Entity

종목의 일별 종가 및 거래량 데이터를 저장합니다.

**필드 설계:**
- `id`: UUID (Primary Key)
- `asset_id`: UUID (Foreign Key → assets)
- `price_date`: LocalDate - 거래일
- `price`: BigDecimal - 종가
- `volume`: Long - 거래량
- `created_at`: LocalDateTime

**Unique Index:** `(asset_id, price_date)` - 중복 방지

### 일일 가격 업데이트 배치 Job

**한국 주식 (krDailyPriceJob):**
- **대상**: `active = true AND type = STOCK`인 모든 한국 주식
- **데이터 소스**: data.go.kr API (`getStockPriceInfo`)
- **처리 로직**:
  1. active한 주식 목록 조회
  2. 각 종목마다 최근 5일치 데이터 요청 (최신 거래일 확보)
  3. 최신 거래일 가격 추출
  4. 중복 체크 (`asset_id + price_date`)
  5. 신규 가격만 저장
- **Rate Limiting**: 100ms 딜레이
- **실행 주기**: 매 평일 18:00 KST (장 마감 15:30 이후)

**한국 ETF (krEtfDailyPriceJob):**
- **대상**: `active = true AND type = ETF`인 모든 한국 ETF
- **데이터 소스**: data.go.kr API (`getETFPriceInfo`)
- **처리 로직**: 주식과 동일
- **Rate Limiting**: 100ms 딜레이
- **실행 주기**: 매 평일 18:00 KST (주식 가격 업데이트 직후)

**미국 주식 (usDailyPriceJob):**
- **대상**: `active = true AND type = STOCK`인 모든 미국 주식
- **데이터 소스**: FMP API (`historical-price-eod`)
- **처리 로직**:
  1. active한 주식 목록 조회
  2. 각 종목마다 최근 3일치 데이터 요청 (최신 거래일 확보)
  3. 최신 거래일 가격 추출 (FMP는 최신순 정렬)
  4. 중복 체크 (`asset_id + price_date`)
  5. 신규 가격만 저장
- **Rate Limiting**: 150ms 딜레이
- **실행 주기**: 매 평일 07:00 KST (미국 장 마감 후, TUE-SAT KST = MON-FRI EST)

**미국 ETF (usEtfDailyPriceJob):**
- **대상**: `active = true AND type = ETF`인 모든 미국 ETF
- **데이터 소스**: FMP API (`historical-price-eod`)
- **처리 로직**: 주식과 동일 (FMP API는 주식/ETF 구분 없이 동일 엔드포인트)
- **Rate Limiting**: 150ms 딜레이
- **실행 주기**: 매 평일 07:00 KST (주식 가격 업데이트 직후)

**배치 스케줄링 (BatchConfig):**
```java
// 한국 시장: 평일 18:00 KST
@Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
public void runKrDailyPriceUpdate()

// 미국 시장: 평일 07:00 KST (화-토, 시차 고려)
@Scheduled(cron = "0 0 7 * * TUE-SAT", zone = "Asia/Seoul")
public void runUsDailyPriceUpdate()
```

**수동 실행:**
```bash
# 한국 주식 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=krDailyPriceJob'

# 한국 ETF 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=krEtfDailyPriceJob'

# 미국 주식 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usDailyPriceJob'

# 미국 ETF 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usEtfDailyPriceJob'
```

---

## 환율 데이터 관리

### ExchangeRate (환율) Entity

일일 환율 정보를 저장합니다.

**필드 설계:**
- `id`: UUID (Primary Key)
- `currency_code`: ENUM (CurrencyCode) - 통화 코드
- `currency_name`: String - 통화명
- `base_rate`: BigDecimal - 매매기준율 (KRW 기준)
- `buy_rate`: BigDecimal - 송금 받을 때 환율 (살 때)
- `sell_rate`: BigDecimal - 송금 보낼 때 환율 (팔 때)
- `exchange_date`: LocalDate - 환율 기준일
- `created_at`: LocalDateTime

**Unique Index:** `(currency_code, exchange_date)` - 중복 방지

**CurrencyCode ENUM:**
```java
public enum CurrencyCode {
    // 주요 통화
    USD, EUR, JPY, GBP, CNY,
    // 아시아/오세아니아
    HKD, TWD, SGD, THB, MYR, IDR, PHP, VND, INR, AUD, NZD,
    // 중동
    SAR, KWD, BHD, AED, QAR, OMR,
    // 유럽
    CHF, SEK, NOK, DKK, CZK, HUF, PLN, RUB,
    // 기타
    CAD, MXN, BRL, ZAR, TRY, ILS, EGP, JOD
    // ... 총 43개 통화
}
```

### 일일 환율 업데이트 배치 Job

**환율 업데이트 (exchangeRateJob):**
- **대상**: 한국수출입은행이 제공하는 모든 통화
- **데이터 소스**: 한국수출입은행 API
  - API: `https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON`
  - 파라미터: `authkey`, `searchdate` (YYYYMMDD), `data=AP01`
- **처리 로직**:
  1. 오늘 날짜 기준으로 환율 데이터 요청
  2. API 응답에서 통화 코드 파싱 (예: "JPY(100)" → "JPY")
  3. CurrencyCode enum에 정의된 통화만 필터링 (지원하지 않는 통화는 자동 스킵)
  4. 중복 체크 (`currency_code + exchange_date`)
  5. Upsert 처리
- **실행 주기**: 매 평일 11:00 KST
  - 한국수출입은행은 평일 10시경 환율 업데이트
  - 11시에 배치 실행하여 최신 데이터 수집

**수동 실행:**
```bash
./gradlew bootRun --args='--spring.batch.job.names=exchangeRateJob'
```

---

## 종목 위험도 관리

### AssetRiskHistory (위험도 이력) Entity

종목의 위험도 계산 이력을 주 단위로 저장합니다.

**필드 설계:**
- `id`: UUID (Primary Key)
- `asset_id`: UUID (Foreign Key → assets)
- `week`: String (YYYY-WW 포맷, 예: "2025-W03")
- `risk_level`: Integer (1~5) - 위험도 레벨
- `risk_score`: BigDecimal - 위험도 점수 (0~100)
- `volatility`: BigDecimal - 변동성 (연율화)
- `max_drawdown`: BigDecimal - 최대낙폭 (MDD)
- `worst_day_return`: BigDecimal - 1일 최악 하락률
- `factors_snapshot`: JSON - 계산에 사용된 추가 요소 스냅샷
- `created_at`: LocalDateTime

**Unique Index:** `(asset_id, week)` - 중복 방지

### 위험도 저장 전략

**설계 원칙:**
- **현재 위험도**: `Asset.currentRiskLevel` 필드에 저장 (1~5)
- **과거 이력**: `AssetRiskHistory` 테이블에 주 단위로 저장
- **위험도는 계산 결과물**: 수익률처럼 정밀 시계열 데이터가 아님
- **서비스에서 필요한 것**: "지금 위험도"
- **히스토리는 설명·분석·메타 리포트용**

### 위험도 계산 방식

**전제 조건:**
- 위험도는 **수익률(returns) 기반**으로 계산
- 환율/가격 단위와 무관하게 미장/한국장 통합 가능
- 일간 로그수익률: `r_t = ln(P_t / P_{t-1})`

**핵심 지표 3개:**

1. **변동성 (Volatility)** - 최근 60 거래일
   - 표준편차를 연율화: `vol = std(r_{t-59..t}) × √252`
   - 의미: "평소에 흔들림이 얼마나 큰가"

2. **최대낙폭 (MDD)** - 최근 252 거래일
   - 누적 가격의 고점 대비 저점 하락 최대치
   - `mdd = max_t(1 - P_t / max(P_{0..t}))`
   - 의미: "한번 무너지면 얼마나 크게 무너졌나"

3. **1일 최악 하락 (Worst Day)** - 최근 252 거래일
   - `worst = min(r_{t-251..t})`
   - 의미: "갭하락/급락 같은 꼬리리스크"

**점수화 (스케일 통일): 퍼센타일 정규화**

미장/한국장/ETF/주식을 같은 스케일로 만들기 위해 퍼센타일 사용:

```
각 자산 i에 대해:
- volPct = percentile_rank(vol_i)
- mddPct = percentile_rank(mdd_i)
- worstPct = percentile_rank(-worst_i)  // worst가 더 큰 급락일수록 위험↑

퍼센타일 범위: 0~1
```

**최종 RiskScore (0~100):**

```
riskScore = 100 × (0.45 × volPct + 0.45 × mddPct + 0.10 × worstPct)

가중치:
- 변동성: 45%
- 최대낙폭: 45%
- 꼬리리스크: 10%
```

**RiskLevel 1~5 매핑 (퀸타일 기준):**

- 0~20 → 1 (Low)
- 20~40 → 2
- 40~60 → 3
- 60~80 → 4
- 80~100 → 5 (High)

**설계 특징:**
- **모든 자산에 동일한 위험도 규칙 적용** (마켓/ETF/주식 구분 없음)
- **위험도는 절대 스케일**: "위험도 4"는 어떤 자산이든 동일한 의미
- **ETF는 자연스럽게 저위험**으로 계산됨 (별도 규칙 불필요)
- **퍼센타일 기반**이라 시장 전체가 불안해져도 "상대적 위험"이 유지

### 주간 위험도 계산 배치 Job

**위험도 계산 (assetRiskJob):**
- **대상**: `active = true`인 모든 종목 (주식 + ETF, 한국 + 미국)
- **데이터 소스**: `asset_prices` 테이블 (일봉 종가)
- **처리 로직**:
  1. 전 자산의 최근 252일 가격 데이터 로딩
  2. 각 자산별로 returns 계산
  3. Volatility, MDD, Worst Day 계산
  4. 전체 자산 기준 퍼센타일 산출
  5. riskScore 계산 (0~100)
  6. riskLevel 매핑 (1~5)
  7. `Asset.currentRiskLevel` 업데이트
  8. `AssetRiskHistory` insert (week, riskLevel, riskScore, volatility, mdd, worstDayReturn)
- **실행 주기**: 매주 일요일 03:00 KST
  - 주간 종목 업데이트 (02:00) 직후 실행
  - 최근 8~12주 데이터 rolling window로 위험도 재계산

**수동 실행:**
```bash
./gradlew bootRun --args='--spring.batch.job.names=assetRiskJob'
```

**데이터 요구사항:**
- **필요한 데이터**: 일봉 종가만 (최소 252일, 부족하면 가능한 범위 내 계산)
- **저장 주기**: 주 1회
- **히스토리 관리**: week(YYYY-WW) 기준으로 과거 위험도 조회 가능

---

## 배치 스케줄 전체 요약

### 주간 스케줄 (일요일)

**02:00 KST** - 종목 데이터 업데이트
```
runWeeklyAssetUpdate()
├─ krAssetJob - 한국 주식 종목 업데이트
├─ krEtfJob - 한국 ETF 종목 업데이트 + 과거 가격
├─ usAssetJob - 미국 주식 종목 업데이트
└─ usEtfJob - 미국 ETF 종목 업데이트 + 과거 가격
```

**03:00 KST** - 위험도 계산
```
runWeeklyRiskUpdate()
└─ assetRiskJob - 종목 위험도 계산 및 업데이트
```

### 일일 스케줄 (평일)

**07:00 KST (화-토)** - 미국 시장 가격 업데이트
```
runUsDailyPriceUpdate()
├─ usDailyPriceJob - 미국 주식 가격
└─ usEtfDailyPriceJob - 미국 ETF 가격
```

**11:00 KST (월-금)** - 환율 업데이트
```
runExchangeRateUpdate()
└─ exchangeRateJob - 환율 데이터
```

**18:00 KST (월-금)** - 한국 시장 가격 업데이트
```
runKrDailyPriceUpdate()
├─ krDailyPriceJob - 한국 주식 가격
└─ krEtfDailyPriceJob - 한국 ETF 가격
```

---

## Base
- Base Path: /api/v1
- Auth: Authorization: Bearer {accessToken}
- Content-Type: application/json
- Date format: ISO-8601 (YYYY-MM-DD for chart points)
- Enum:
    - PortfolioStatus: DRAFT | ACTIVE | FINISHED

## Screen List (MVP)
1) Login
2) Home (main portfolio widget only)
3) Portfolio List
4) Portfolio Create Start
5) Arena Round (pick 1 of 3 assets)
6) Portfolio Create Complete
7) Portfolio Detail
8) Portfolio Performance Chart (tab or separate)
9) Asset Detail

---

# 1) Auth / User

## POST /auth/signup
Request
{
"email": "string",
"password": "string",
"nickname": "string"
}
Response
{
"accessToken": "string",
"refreshToken": "string"
}

## GET /auth/check-email?email=string
Check if email is available for signup

Response
{
"available": true|false
}

## POST /auth/login
Request
{
"provider": "EMAIL",
"email": "string",
"password": "string"
}
Response
{
"accessToken": "string",
"refreshToken": "string"
}

## POST /auth/refresh
Request
{
"refreshToken": "string"
}
Response
{
"accessToken": "string",
"refreshToken": "string"
}

## GET /me
Response
{
"userId": "uuid",
"nickname": "string",
"mainPortfolioId": "uuid|null"
}

## PATCH /me
Request
{
"nickname": "string"
}
Response
{
"userId": "uuid",
"nickname": "string"
}

---

# 2) Home (Main Portfolio Widget)

## GET /home
Response (when no main portfolio)
{
"hasMainPortfolio": false
}

Response (when has main portfolio)
{
"hasMainPortfolio": true,
"mainPortfolio": {
"portfolioId": "uuid",
"name": "string",
"startedAt": "YYYY-MM-DD",
"totalReturnPct": 12.34
},
"chart": [
{ "date": "YYYY-MM-DD", "value": 100.0 }
],
"positions": [
{
"assetId": "uuid",
"ticker": "string",
"name": "string",
"weightPct": 25.0,
"returnPct": 18.3
}
]
}

## PUT /portfolios/{portfolioId}/main
Response
{ "mainPortfolioId": "uuid" }

## DELETE /portfolios/main
Response
{ "mainPortfolioId": null }

---

# 3) Portfolio List

## GET /portfolios
Response
[
{
"portfolioId": "uuid",
"name": "string",
"status": "DRAFT|ACTIVE|FINISHED",
"isMain": true,
"totalReturnPct": 12.34,
"createdAt": "YYYY-MM-DD"
}
]

---

# 4) Portfolio (CRUD minimal for MVP)

## POST /portfolios
Request
{
"name": "string"
}
Response
{
"portfolioId": "uuid",
"name": "string",
"status": "DRAFT",
"createdAt": "YYYY-MM-DD"
}

## GET /portfolios/{portfolioId}
Response
{
"portfolioId": "uuid",
"name": "string",
"status": "DRAFT|ACTIVE|FINISHED",
"isMain": true,
"startedAt": "YYYY-MM-DD|null",
"totalReturnPct": 12.34,
"positions": [
{
"assetId": "uuid",
"ticker": "string",
"name": "string",
"weightPct": 25.0,
"returnPct": 18.3
}
]
}

## POST /portfolios/{portfolioId}/start
Response
{
"portfolioId": "uuid",
"status": "ACTIVE",
"startedAt": "YYYY-MM-DD"
}

---

# 5) Arena (Hearthstone-style drafting)

## POST /arena/sessions
Request
{
"portfolioId": "uuid"
}
Response
{
"sessionId": "uuid",
"portfolioId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 1
}

## GET /arena/sessions/{sessionId}/rounds/current
Response
{
"sessionId": "uuid",
"round": 1,
"assets": [
{
"assetId": "uuid",
"ticker": "string",
"name": "string",
"oneLineThesis": "string",
"tags": ["string"]
}
]
}

## POST /arena/sessions/{sessionId}/rounds/current/pick
Request
{
"pickedAssetId": "uuid"
}
Response
{
"sessionId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 2,
"picked": {
"assetId": "uuid"
}
}

## GET /arena/sessions/{sessionId}
Response
{
"sessionId": "uuid",
"portfolioId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 3,
"totalRounds": 10
}

---

# 6) Portfolio Performance

## GET /portfolios/{portfolioId}/performance?range=1M|3M|1Y
Response
{
"portfolioId": "uuid",
"range": "1M",
"points": [
{ "date": "YYYY-MM-DD", "value": 100.0 }
]
}

---

# 7) Assets (종목)

## GET /assets/search?query=string
Response
[
{
"assetId": "uuid",
"ticker": "string",
"name": "string",
"exchange": "string|null",
"country": "string|null",
"sector": "string|null",
"imageUrl": "string|null"
}
]

## GET /assets/{assetId}
Response
{
"assetId": "uuid",
"ticker": "string",
"name": "string",
"exchange": "string|null",
"country": "string|null",
"sector": "string|null",
"currency": "string|null",
"imageUrl": "string|null",
"description": "string|null"
}

## GET /assets/{assetId}/chart?range=1M|3M|1Y
Response
{
"assetId": "uuid",
"range": "1M",
"points": [
{ "date": "YYYY-MM-DD", "price": 123.45 }
]
}

## GET /assets/{assetId}/in-my-main-portfolio
Response (not included)
{ "included": false }

Response (included)
{
"included": true,
"portfolioId": "uuid",
"weightPct": 25.0,
"returnPct": 18.3
}