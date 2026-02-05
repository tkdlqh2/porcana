# Porcana MVP - Project Overview

> **Note**: 상세 내용은 `.claude/skills/` 디렉토리의 각 skill을 참조하세요.
> - `api-conventions`: API 설계 패턴
> - `api-specs`: API 엔드포인트 상세 스펙
> - `entity-reference`: Entity 구조 및 관계
> - `batch-jobs`: Spring Batch 작업 레퍼런스
> - `arena-specs`: Arena 알고리즘 상세
> - `test-patterns`: 테스트 작성 패턴

---

## Technology Stack

### Core Technologies
- **Spring Boot 3.2.1** - Application framework
- **PostgreSQL** - Primary database
- **Spring Batch** - Batch processing
- **Spring Security + JWT** - Authentication
- **QueryDSL 5.0.0** - Type-safe queries
- **Flyway** - Database migration
- **Swagger/OpenAPI** - API documentation

### Key Libraries
- **RestTemplate** - External API calls (data.go.kr, FMP)
- **Lombok** - Boilerplate reduction
- **Jackson** - JSON processing
- **HikariCP** - Connection pooling

### Development Tools
- **Discord Webhook** - Batch monitoring & alerts
- **Spring Boot Actuator** - Health checks & metrics

---

## Development Philosophy

### 1. Request DTO as Record

**핵심 규칙:**
- **Request DTO**: Java Record로 작성 (불변성)
- **Response/Command DTO**: @Builder + Lombok 사용
- **Validation**: Record 필드에 직접 annotation 적용

**Example:**
```java
public record SignupRequest(
    @Email(message = "올바른 이메일 형식이 아닙니다")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    String password,

    @NotBlank(message = "닉네임은 필수입니다")
    String nickname
) {}
```

**Why:**
- Record는 불변 객체로 API 요청 데이터에 적합
- getter 자동 생성 (`request.email()`)
- equals/hashCode/toString 자동 생성

> 📖 **자세한 내용**: `.claude/skills/api-conventions/SKILL.md` 참조

---

### 2. Command Pattern

**Flow:**
```
Request DTO (SignupRequest)
    ↓ Command.from(request)
Command DTO (SignupCommand)
    ↓ Service 처리
Entity (User.from(command))
```

**Example:**
```java
// Controller
@PostMapping("/signup")
public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
    SignupCommand command = SignupCommand.from(request);
    AuthResponse response = authService.signup(command);
    return ResponseEntity.ok(response);
}

// Command 정적 팩토리
public static SignupCommand from(SignupRequest request) {
    return SignupCommand.builder()
        .email(request.email())
        .password(request.password())
        .nickname(request.nickname())
        .provider(User.AuthProvider.EMAIL)
        .build();
}
```

**Why:**
- 계층 간 명확한 책임 분리
- Request DTO는 API 스펙에 종속, Command는 도메인 로직에 집중

> 📖 **자세한 내용**: `.claude/skills/api-conventions/SKILL.md` 참조

---

### 3. Swagger Authentication (JWT)

**인증 필요 API:**
```java
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/api/v1")
public class UserController {
    // JWT 필요, Swagger UI에서 자물쇠 표시
}
```

**공개 API:**
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    // @SecurityRequirement 없음 = 공개 API
}
```

**@CurrentUser Annotation:**
```java
@GetMapping("/me")
public ResponseEntity<UserResponse> getMe(@CurrentUser UUID userId) {
    // JWT 토큰에서 userId 자동 추출
}
```

> 📖 **자세한 내용**: `.claude/skills/api-conventions/SKILL.md` 참조

---

## Data Model Overview

### Core Entities

**Asset (종목)**
- 시장: KR | US
- 타입: STOCK | ETF
- Sector (주식 전용), AssetClass (ETF 전용)
- `currentRiskLevel`: 1~5 (현재 위험도)
- `active`: 카드 풀 포함 여부
- `imageUrl`: 로고 이미지 URL

**AssetPrice (가격 데이터)**
- **OHLC 일봉 데이터**: Open, High, Low, Close
- `volume`: 거래량
- `priceDate`: 가격 날짜
- Unique constraint: (asset_id, price_date)
- 하위호환: `getPrice()` → closePrice 반환

**Portfolio (포트폴리오)**
- 상태: DRAFT | ACTIVE | FINISHED
- 소유권: `user_id` XOR `guest_session_id` (XOR 제약)
- 게스트는 최대 3개 포트폴리오

**ArenaSession (아레나 드래프트)**
- Round 0: Risk Profile + Sector 선택
- Round 1-10: Asset 선택 (3개 중 1개)
- Round 10 완료 시 자동으로 포트폴리오 ACTIVE화

**Guest Session (비회원 지원)**
- 비회원도 포트폴리오 생성 가능
- 회원가입/로그인 시 소유권 이전 (claim)
- 30일 만료 정책

> 📖 **상세 스키마**: `.claude/skills/entity-reference/SKILL.md` 참조

---

## Batch Strategy Overview

### 주간 스케줄 (일요일)

**02:00 KST - 종목 데이터 업데이트**
- krAssetJob, krEtfJob (한국)
- usAssetJob, usEtfJob (미국)

**03:00 KST - 위험도 계산**
- assetRiskJob (전 종목 위험도 계산)

### 일일 스케줄 (평일)

**07:00 KST (화-토) - 미국 시장**
- usDailyPriceJob, usEtfDailyPriceJob

**12:00 KST (월-금) - 환율**
- exchangeRateJob

**18:00 KST (월-금) - 한국 시장**
- krDailyPriceJob, krEtfDailyPriceJob

### 배치 모니터링

**Discord Webhook 알림**
- 모든 배치 작업의 성공/실패/경고 알림
- 자동으로 작업 실행 결과, 소요 시간, 에러 정보 전송
- 설정: `application.yml`에서 `notification.discord` 설정

> 📖 **Discord 설정 가이드**: `DISCORD_NOTIFICATION_GUIDE.md` 참조

### 위험도 계산 방식

**핵심 지표:**
1. Volatility (변동성) - 최근 60 거래일
2. MDD (최대낙폭) - 최근 252 거래일
3. Worst Day (최악 하락) - 최근 252 거래일

**RiskScore:**
```
riskScore = 100 × (0.45 × volPct + 0.45 × mddPct + 0.10 × worstPct)
```

**RiskLevel 매핑:**
- 0~20 → 1 (Low)
- 20~40 → 2
- 40~60 → 3
- 60~80 → 4
- 80~100 → 5 (High)

> 📖 **상세 스펙**: `.claude/skills/batch-jobs/SKILL.md` 참조

---

## Arena Algorithm Overview

### Round 구조
- **Round 0 (Pre Round)**: Risk Profile + Sector (0-3개) 선택
- **Rounds 1-10**: Asset 선택 (3개 중 1개)

### 추천 전략
- **Normal Picks (2개)**: 선호 섹터 + 리스크 프로필 반영
- **Wild Pick (1개)**: 섹터 무시, 의외성 보장

### Weight 계산
```java
w = riskWeight × sectorWeight × typeWeight × diversityPenalty
```

**Key Weights:**
- ETF: 2.5x (다양성 확보)
- 선호 섹터: 1.5x
- SAFE 프로필: riskLevel 1-2 → 1.4x
- BALANCED: riskLevel 2-4 → 1.2x
- AGGRESSIVE: riskLevel 4-5 → 1.4x

### Diversity Condition
라운드당 3개 선택지:
- 최소 2개 섹터
- 최소 2개 리스크 밴드 (LOW/MID/HIGH)

### Query Optimization (Bucket Sampling)
- Preferred: 80개
- Non-Preferred: 40개
- Wild: 20개
- **Total: ~140개** (전체 1000개 대비 86% 절감)

> 📖 **상세 알고리즘**: `.claude/skills/arena-specs/SKILL.md` 참조

---

## Screen List (MVP)

1. **Login** - 로그인/회원가입
2. **Home** - 메인 포트폴리오 위젯
3. **Portfolio List** - 포트폴리오 목록
4. **Portfolio Create Start** - 포트폴리오 생성 시작
5. **Arena Round** - 아레나 드래프트 (3개 중 1개 선택)
6. **Portfolio Create Complete** - 생성 완료
7. **Portfolio Detail** - 포트폴리오 상세
8. **Portfolio Performance Chart** - 수익률 차트
9. **Asset Detail** - 종목 상세

---

## API Endpoints Quick Reference

### Base Configuration
- Base Path: `/api/v1`
- Auth: `Authorization: Bearer {accessToken}`
- Content-Type: `application/json`
- Date format: ISO-8601

### Endpoint Groups

**0) Guest Session**
- `POST /guest-sessions` - 게스트 세션 생성

**1) Auth / User**
- `POST /auth/signup` - 회원가입 (OAuth: GOOGLE, APPLE)
- `GET /auth/check-email` - 이메일 중복 확인
- `POST /auth/login` - 로그인 (EMAIL, GOOGLE, APPLE)
- `POST /auth/refresh` - 토큰 갱신
- `GET /me` - 사용자 정보 조회
- `PATCH /me` - 사용자 정보 수정

**2) Home**
- `GET /home` - 홈 화면 (메인 포트폴리오 위젯)
- `PUT /portfolios/{portfolioId}/main` - 메인 포트폴리오 설정

**3) Portfolio**
- `GET /portfolios` - 포트폴리오 목록 (ACTIVE, FINISHED만)
- `POST /portfolios` - 포트폴리오 생성 (비회원 가능)
- `GET /portfolios/{portfolioId}` - 포트폴리오 상세
- `GET /portfolios/{portfolioId}/performance` - 수익률 차트

**4) Arena**
- `POST /arena/sessions` - 아레나 세션 시작
- `GET /arena/sessions/{sessionId}` - 세션 정보 조회
- `GET /arena/sessions/{sessionId}/rounds/current` - 현재 라운드 조회
- `POST /arena/sessions/{sessionId}/rounds/current/pick-preferences` - Round 0 선택
- `POST /arena/sessions/{sessionId}/rounds/current/pick-asset` - Round 1-10 선택

**5) Assets**
- `GET /assets/search?query=string` - 종목 검색
- `GET /assets/{assetId}` - 종목 상세
- `GET /assets/{assetId}/chart` - 가격 차트
- `GET /assets/{assetId}/in-my-main-portfolio` - 메인 포트폴리오 포함 여부

> 📖 **API 상세 스펙**: `.claude/skills/api-specs/SKILL.md` 참조

---

## Quick Start

### 배치 작업 실행
```bash
# 한국 주식 종목 업데이트
./gradlew bootRun --args='--spring.batch.job.names=krAssetBatchJob'

# 미국 주식 종목 업데이트
./gradlew bootRun --args='--spring.batch.job.names=usAssetBatchJob'

# 일일 가격 업데이트 (한국)
./gradlew bootRun --args='--spring.batch.job.names=krDailyPriceJob'

# 일일 가격 업데이트 (미국)
./gradlew bootRun --args='--spring.batch.job.names=usDailyPriceJob'

# 환율 업데이트
./gradlew bootRun --args='--spring.batch.job.names=exchangeRateJob'

# 위험도 계산
./gradlew bootRun --args='--spring.batch.job.names=assetRiskJob'

# OHLC 데이터 백필 (일회성)
OHLC_BACKFILL_ENABLED=true ./gradlew bootRun
```

> 📖 **배치 작업 상세**: `.claude/skills/batch-jobs/SKILL.md` 참조

---

## 참고 자료

### Skills (도메인별 참고 자료)

**API & Conventions:**
- `api-conventions` - API 설계 패턴 및 컨벤션
- `api-specs` - API 엔드포인트 상세 스펙

**Domain Models:**
- `entity-reference` - Core Entity 구조 및 관계
- `portfolio-domain` - 포트폴리오 수익률 추적 (Snapshot, DailyReturn, weightUsed)

**Business Logic:**
- `arena-specs` - Arena 드래프트 알고리즘
- `batch-jobs` - Spring Batch 작업 레퍼런스

**Testing:**
- `test-patterns` - 테스트 작성 패턴

### Subagents (특수 작업용 AI 어시스턴트)
- `arena-analyzer` - Arena 로직 분석 및 검증
- `batch-debugger` - Batch 작업 디버깅
- `api-tester` - API 엔드포인트 테스트
- `security-checker` - 보안 취약점 스캔
- `performance-analyzer` - 성능 분석 및 최적화

> 📖 **Skills & Subagents 가이드**: `.claude/README.md` 참조

---

**이제 Claude Code가 필요한 정보만 선택적으로 로드하여 토큰 효율성을 극대화합니다!** 🚀