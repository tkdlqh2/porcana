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
        .email(request.getEmail())
        .password(request.getPassword())
        .nickname(request.getNickname())
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
@RequestMapping("/app/v1")
public class UserController {
    // 이 컨트롤러의 모든 엔드포인트에 JWT 필요
    // Swagger UI에서 자물쇠 아이콘 표시됨
}
```

**인증이 필요없는 API:**
```java
@RestController
@RequestMapping("/auth")
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

## Base
- Base Path: /app/v1
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