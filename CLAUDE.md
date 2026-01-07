# Porcana MVP API Contract (Frontend 공유용)

## Development Philosophy

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