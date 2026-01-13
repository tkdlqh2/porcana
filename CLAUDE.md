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
- `sector`: ENUM (Sector) - GICS 표준 섹터 (MATERIALS, COMMUNICATION_SERVICES, CONSUMER_DISCRETIONARY, CONSUMER_STAPLES, ENERGY, FINANCIALS, HEALTH_CARE, INDUSTRIALS, REAL_ESTATE, INFORMATION_TECHNOLOGY, UTILITIES)
- `universe_tags`: List<UniverseTag> - 유니버스 태그 (SP500, NASDAQ100, KOSPI200, KOSDAQ150 등)
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
    private Sector sector;  // GICS 표준 섹터

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

**한국 마켓 (krDailyPriceJob):**
- **대상**: `active = true`인 모든 한국 종목
- **데이터 소스**: data.go.kr API (`getStockPriceInfo`)
- **처리 로직**:
  1. active한 종목 목록 조회
  2. 각 종목마다 최근 5일치 데이터 요청 (최신 거래일 확보)
  3. 최신 거래일 가격 추출
  4. 중복 체크 (`asset_id + price_date`)
  5. 신규 가격만 저장
- **Rate Limiting**: 100ms 딜레이
- **실행 주기**: 매 평일 18:00 KST (장 마감 15:30 이후)

**미국 마켓 (usDailyPriceJob):**
- **대상**: `active = true`인 모든 미국 종목
- **데이터 소스**: FMP API (`historical-price-eod`)
- **처리 로직**:
  1. active한 종목 목록 조회
  2. 각 종목마다 최근 3일치 데이터 요청 (최신 거래일 확보)
  3. 최신 거래일 가격 추출 (FMP는 최신순 정렬)
  4. 중복 체크 (`asset_id + price_date`)
  5. 신규 가격만 저장
- **Rate Limiting**: 150ms 딜레이
- **실행 주기**: 매 평일 07:00 KST (미국 장 마감 후, TUE-SAT KST = MON-FRI EST)

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
# 한국 시장 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=krDailyPriceJob'

# 미국 시장 일일 가격 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usDailyPriceJob'
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