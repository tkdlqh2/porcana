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
    USD, JPY, EUR, CNY,
    // 유럽
    GBP, CHF, SEK, CZK, DKK, NOK, HUF, PLN, RUB, TRY,
    // 아시아/오세아니아
    HKD, TWD, SGD, THB, MYR, IDR, PHP, VND, INR, AUD, NZD,
    PKR, BDT, MNT, KZT, BND, FJD,
    // 중동
    SAR, KWD, BHD, AED, QAR, OMR, JOD, ILS, EGP,
    // 아메리카
    CAD, MXN, BRL, CLP,
    // 아프리카
    ZAR
    // 총 44개 통화
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
"refreshToken": "string",
"user": {
"userId": "uuid",
"nickname": "string",
"mainPortfolioId": null
}
}

## GET /auth/check-email?email=string
Check if email is available for signup

Response
{
"available": true|false
}

## POST /auth/login
**지원 Provider**: EMAIL, GOOGLE, APPLE

Request (EMAIL provider)
{
"provider": "EMAIL",
"email": "string",
"password": "string"
}

Request (OAuth providers - GOOGLE, APPLE)
{
"provider": "GOOGLE|APPLE",
"code": "string"
}

Response
{
"accessToken": "string",
"refreshToken": "string",
"user": {
"userId": "uuid",
"nickname": "string",
"mainPortfolioId": "uuid|null"
}
}

**Validation Notes:**
- EMAIL provider: email, password 필수
- GOOGLE/APPLE provider: code 필수 (OAuth authorization code)
- 커스텀 validator (@ValidLoginRequest)로 provider별 필수 필드 검증

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
**Description**: 현재 인증된 사용자의 최신 정보를 조회합니다. 로그인/회원가입 시 user 정보가 응답에 포함되지만, 이 API는 토큰으로 최신 유저 정보를 조회할 때 사용합니다.

**Auth**: Required (JWT)
Response
{
"userId": "uuid",
"nickname": "string",
"mainPortfolioId": "uuid|null"
}

## PATCH /me
**Auth**: Required (JWT)

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
**Description**: 포트폴리오에 대한 새로운 아레나 드래프트 세션을 시작합니다. 이미 진행 중인 세션이 있으면 해당 세션을 반환합니다.

**Auth**: Required (JWT)

Request
{
"portfolioId": "uuid"
}

Response (200 OK)
{
"sessionId": "uuid",
"portfolioId": "uuid",
"status": "IN_PROGRESS",
"currentRound": 0
}

Error Responses
- 400: 포트폴리오를 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

## GET /arena/sessions/{sessionId}
**Description**: 진행 중이거나 완료된 아레나 세션의 상세 정보를 조회합니다.

**Auth**: Required (JWT)

Response (200 OK)
{
"sessionId": "uuid",
"portfolioId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 3,
"totalRounds": 11,
"riskProfile": "BALANCED",
"selectedSectors": ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"],
"selectedAssetIds": ["uuid1", "uuid2"]
}

Error Responses
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

## GET /arena/sessions/{sessionId}/rounds/current
**Description**: 현재 진행 중인 라운드의 선택지를 조회합니다.
- Round 0: 투자 성향 + 섹터 동시 선택 (Pre Round)
- Round 1-10: 자산 선택

**Auth**: Required (JWT)

Response for Round 0 (Pre Round - Risk Profile + Sector Selection)
{
"sessionId": "uuid",
"round": 0,
"roundType": "PRE_ROUND",
"riskProfileOptions": [
{
"value": "AGGRESSIVE",
"displayName": "공격적",
"description": "고위험 고수익을 추구하는 투자 성향"
},
{
"value": "BALANCED",
"displayName": "균형",
"description": "위험과 수익의 균형을 추구하는 투자 성향"
},
{
"value": "SAFE",
"displayName": "보수적",
"description": "안정적인 수익을 추구하는 저위험 투자 성향"
}
],
"sectorOptions": [
{
"value": "INFORMATION_TECHNOLOGY",
"displayName": "정보기술",
"assetCount": 45
},
{
"value": "HEALTH_CARE",
"displayName": "헬스케어",
"assetCount": 38
},
// ... more sectors
],
"minSectorSelection": 0,
"maxSectorSelection": 3
}

**Field Notes:**
- `riskProfileOptions`: 투자 성향 선택지 (SAFE/BALANCED/AGGRESSIVE 중 1개 필수 선택)
- `sectorOptions`: 섹터 선택지 (0-3개 선택 가능)
- `value`: Sector enum 값
- `displayName`: 한국어 섹터명
- `assetCount`: 해당 섹터에 속한 활성 자산 개수

Response for Round 1-10 (Asset Selection)
{
"sessionId": "uuid",
"round": 3,
"roundType": "ASSET",
"assets": [
{
"assetId": "uuid",
"ticker": "AAPL",
"name": "Apple Inc.",
"sector": "INFORMATION_TECHNOLOGY",
"tags": ["SP500", "NASDAQ100"]
},
{
"assetId": "uuid",
"ticker": "MSFT",
"name": "Microsoft Corp.",
"sector": "INFORMATION_TECHNOLOGY",
"tags": ["SP500"]
},
{
"assetId": "uuid",
"ticker": "JNJ",
"name": "Johnson & Johnson",
"sector": "HEALTH_CARE",
"tags": ["SP500"]
}
]
}

**Field Notes:**
- `tags`: UniverseTag 목록 (예: SP500, NASDAQ100, KOSPI200, KOSDAQ150)
- riskLevel, market 필드는 제외됨 (클라이언트 표시용 최소 정보만 제공)

Error Responses
- 400: 세션이 이미 완료됨
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

## POST /arena/sessions/{sessionId}/rounds/current/pick-preferences
**Description**: 아레나 Round 0 (Pre Round)에서 투자 성향(리스크 프로필)과 관심 섹터를 동시에 선택합니다. 0-3개의 섹터를 선택할 수 있으며, 중복은 허용되지 않습니다.

**Auth**: Required (JWT)

Request
{
"riskProfile": "SAFE|BALANCED|AGGRESSIVE",
"sectors": ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"]
}

Response (200 OK)
{
"sessionId": "uuid",
"status": "IN_PROGRESS",
"currentRound": 1,
"picked": {
"riskProfile": "BALANCED",
"sectors": ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"]
}
}

Error Responses
- 400: Round 0이 아니거나 섹터 개수가 3개 초과 또는 중복된 섹터 포함
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

## POST /arena/sessions/{sessionId}/rounds/current/pick-asset
**Description**: 아레나 Round 1-10에서 제시된 3개의 자산 중 1개를 선택합니다. Round 10 완료 시 세션이 종료되고 포트폴리오가 완성됩니다.

**Auth**: Required (JWT)

Request
{
"pickedAssetId": "uuid"
}

Response (200 OK)
{
"sessionId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 2,
"picked": "uuid"
}

Error Responses
- 400: Round 1-10이 아니거나 제시된 자산 목록에 없는 자산 선택
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

# 5-1) Arena Asset Recommendation Logic

## Overview
Arena는 Hearthstone-style의 드래프트 시스템으로, 사용자가 3개의 선택지 중 1개를 선택하여 포트폴리오를 구성합니다.

## Round Structure
- **Round 0 (Pre Round)**: Risk Profile + Sector 동시 선택 (SAFE/BALANCED/AGGRESSIVE + 0-3개 섹터)
- **Rounds 1-10**: Asset 선택 (라운드당 3개 중 1개)

## Asset Recommendation Algorithm

### Entry Point
```java
List<Asset> generateRoundOptions(ArenaSession session, int roundNo)
```

### Input Context
- `riskProfile`: SAFE | BALANCED | AGGRESSIVE
- `preferredSectors`: 사용자가 선택한 2-3개 섹터
- `deckAssetIds`: 이미 선택된 자산 (중복 방지)
- `shownAssetIds`: 이전 라운드에 제시된 자산 (재사용 방지, 후보 부족 시 완화)

### Optimization Strategy
**메모리 효율성을 위해 전체 ~1000개 assets를 로드하지 않고, 필요한 subset만 조회:**

1. **Preferred Candidates (Normal Picks용)**
   ```java
   List<Asset> preferredCandidates = assetRepository.findBySectorInAndActiveTrue(preferredSectors);
   // 선호 섹터 (2-3개)에 속한 자산만 로드 (약 200-300개)
   ```

2. **Wild Candidates (Wild Pick용)**
   ```java
   List<Asset> wildCandidates = assetRepository.findBySectorNotInAndActiveTrue(preferredSectors);
   // 선호 섹터가 아닌 자산들 (의외성 보장)
   ```

3. **Filter Excluded Assets**
   ```java
   candidates = filterExcludedAssets(candidates, deckAssetIds + shownAssetIds);
   ```

### Selection Process

#### 1) Normal Picks (2개)
선호 섹터 + 리스크 프로필 + 다양성 패널티 반영

```java
for i in 1..2:
  next = weightedPickOne(preferredCandidates, riskProfile, preferredSectors, alreadyPicked, useSectorPreference=true);
  picked.add(next);
  preferredCandidates.remove(next);
  wildCandidates.remove(next);
```

#### 2) Wild Pick (1개)
섹터 선호 무시, 다양성만 유지 (의외성 보장)

```java
wild = weightedPickOne(wildCandidates, riskProfile, preferredSectors, alreadyPicked, useSectorPreference=false);
picked.add(wild);
```

### Weighted Selection Logic

#### Weight Calculation
```java
w = 1.0
w *= riskWeight(riskProfile, asset.currentRiskLevel);
w *= sectorWeight(preferredSectors, asset.sector);  // normal picks만
w *= typeWeight(asset.type);
w *= diversityPenalty(asset, alreadyPicked);
w = max(w, 0.0001);  // 안전장치
```

#### Risk Weight
```java
riskWeight(RiskProfile profile, Integer riskScore):
  if profile == SAFE:
    if riskScore in [1,2]: return 1.4
    if riskScore == 3: return 1.0
    return 0.6  // 4~5
  if profile == BALANCED:
    if riskScore in [2,3,4]: return 1.2
    return 0.8  // 1 or 5
  if profile == AGGRESSIVE:
    if riskScore in [4,5]: return 1.4
    if riskScore == 3: return 1.0
    return 0.7  // 1~2
```

#### Sector Weight
```java
sectorWeight(Set<Sector> preferredSectors, Sector sector):
  if preferredSectors.isEmpty(): return 1.0
  return preferredSectors.contains(sector) ? 1.5 : 1.0
```

#### Type Weight
```java
typeWeight(AssetType assetType):
  return assetType == ETF ? 1.05 : 1.0
  // ETF를 "가볍게" 선호 (과도하지 않게)
```

#### Diversity Penalty
```java
diversityPenalty(Asset candidate, List<Asset> alreadyPicked):
  penalty = 1.0

  // 같은 섹터면 확률 크게 다운
  if alreadyPicked.any(p => p.sector == candidate.sector):
    penalty *= 0.35

  // 리스크 밴드 겹치면 조금 다운
  candBand = riskBand(candidate.riskScore)  // LOW(1-2) | MID(3) | HIGH(4-5)
  if alreadyPicked.any(p => riskBand(p.riskScore) == candBand):
    penalty *= 0.70

  return penalty
```

### Diversity Condition
라운드당 3개 선택지는 다음 조건을 만족해야 함:

```java
isDiverseEnough(List<Asset> picked):
  // 최소 2개 섹터
  distinctSectors = picked.map(p => p.sector).distinct().size
  if distinctSectors < 2: return false

  // 최소 2개 리스크 밴드
  distinctBands = picked.map(p => riskBand(p.riskScore)).distinct().size
  return distinctBands >= 2
```

**Retry Logic:**
- 다양성 조건 실패 시 최대 5회 재시도
- 재시도 시 `shownAssetIds` 제약 완화

### Fallback Strategy
후보 부족 시 제약 조건을 단계적으로 완화:

1. **First Try**: `shownAssetIds` 제외
2. **Fallback**: `shownAssetIds` 제약 완화, `deckAssetIds`만 제외
3. **Last Resort**: 다양성 조건 완화 (경고 로그)

### Memory Efficiency
**Before (비효율):**
```java
List<Asset> allAssets = assetRepository.findByActiveTrue();  // ~1000개
// 메모리: ~100-200KB per round
```

**After (효율):**
```java
List<Asset> preferredCandidates = assetRepository.findBySectorInAndActiveTrue(preferredSectors);  // ~200-300개
List<Asset> wildCandidates = assetRepository.findBySectorNotInAndActiveTrue(preferredSectors);
// 메모리: 30-50% 절감
```

### Example Flow
```
Round 3:
  preferredSectors = [INFORMATION_TECHNOLOGY, HEALTH_CARE]
  riskProfile = BALANCED
  deckAssetIds = []
  shownAssetIds = []

1. Load preferredCandidates (IT + Healthcare assets, ~150개)
2. Load wildCandidates (other sectors, ~650개)
3. Pick 2 normal from preferredCandidates (섹터 선호 1.5배, 리스크 가중치)
4. Pick 1 wild from wildCandidates (섹터 무시, 의외성)
5. Check diversity (2+ sectors, 2+ risk bands)
6. Return 3 assets

Result:
  [
    { assetId: "...", ticker: "AAPL", name: "Apple Inc.", sector: "INFORMATION_TECHNOLOGY", tags: ["SP500"] },
    { assetId: "...", ticker: "JNJ", name: "Johnson & Johnson", sector: "HEALTH_CARE", tags: ["SP500"] },
    { assetId: "...", ticker: "XOM", name: "Exxon Mobil", sector: "ENERGY", tags: ["SP500"] }  // wild pick
  ]
```

### Query Optimization (Bucket Sampling)

**Problem:**
전체 active assets (~1000개)를 로드하는 것조차 여전히 비효율적. 특히 `ORDER BY random()`은 대용량에서 성능 저하.

**Solution: Bucket Sampling**
DB에서 **선호 섹터 버킷 + 비선호 버킷 + 와일드 버킷**으로 각각 소량 샘플링 → 앱에서 가중치 계산

#### Bucket Sizes (Default)
- **Preferred Sector Candidates**: 80개
- **Non-Preferred Sector Candidates**: 40개
- **Wild Candidates**: 20개
- **Total**: ~140개 (전체 1000개 대비 86% 절감)

#### 1) Preferred Sector Bucket (80개)
```sql
SELECT a.id, a.sector, a.current_risk_level, a.type, a.market
FROM assets a
WHERE a.active = true
  AND a.sector = ANY(:preferred_sectors)
  AND a.id <> ALL(:deck_asset_ids)
  AND a.id <> ALL(:shown_asset_ids)
  AND a.id >= :rand_id
ORDER BY a.id
LIMIT 80;
```

#### 2) Non-Preferred Sector Bucket (40개)
```sql
SELECT a.id, a.sector, a.current_risk_level, a.type, a.market
FROM assets a
WHERE a.active = true
  AND (a.sector <> ALL(:preferred_sectors) OR :preferred_sectors_is_empty = true)
  AND a.id <> ALL(:deck_asset_ids)
  AND a.id <> ALL(:shown_asset_ids)
  AND a.id >= :rand_id
ORDER BY a.id
LIMIT 40;
```

#### 3) Wild Bucket (20개)
```sql
SELECT a.id, a.sector, a.current_risk_level, a.type, a.market
FROM assets a
WHERE a.active = true
  AND a.id <> ALL(:deck_asset_ids)
  AND a.id <> ALL(:shown_asset_ids)
  AND a.id >= :rand_id
ORDER BY a.id
LIMIT 20;
```

#### Random Sampling Strategy (PK Range Random)

**ORDER BY random() 금지**: 대용량 테이블에서 성능 최악

### MVP 추천: PK 범위 랜덤
1. 앱에서 `minId ~ maxId` 캐시 (또는 하드코딩)
2. `rand_id = random(minId, maxId)` 생성
3. `WHERE a.id >= :rand_id ORDER BY a.id LIMIT N`
4. 부족하면 wrap-around: `WHERE a.id < :rand_id ORDER BY a.id LIMIT N`

**장점:**
- 빠름 (인덱스 활용)
- 구현 간단
- MVP에 충분

**단점:**
- ID 분포가 매우 불균형이면 편향 가능 (대부분 괜찮음)

**Advanced (운영 단계):**
- `assets` 테이블에 `rand_key DOUBLE PRECISION` (0~1) 컬럼 추가
- 배치로 주기적으로 랜덤 값 갱신
- 인덱스: `(sector, rand_key)`
- 쿼리: `WHERE a.rand_key >= :rk ORDER BY a.rand_key LIMIT N`
- 진짜 빠르고 깔끔함

#### Required Indexes
```sql
CREATE INDEX idx_asset_sector_active ON assets(sector, active);
CREATE INDEX idx_asset_active_id ON assets(active, id);
CREATE INDEX idx_asset_risk_level ON assets(current_risk_level);
```

#### Optimization Benefits
- **메모리**: 1000개 → 140개 (86% 절감)
- **쿼리 성능**: ORDER BY random() 제거 → PK range scan
- **네트워크**: 데이터 전송량 86% 감소

#### Implementation Flow
```
1. DB에서 3개 버킷 샘플링 (총 140개)
   - Preferred: 80
   - Non-Preferred: 40
   - Wild: 20

2. App에서 가중치 계산 (140개에 대해)
   - riskWeight × sectorWeight × diversityPenalty

3. Weighted sampling으로 최종 3개 선택
   - Normal picks: 2개 (preferred 우선)
   - Wild pick: 1개 (wild bucket 우선)

4. Diversity check (최대 5회 재시도)

5. Round에 저장 및 반환
```

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