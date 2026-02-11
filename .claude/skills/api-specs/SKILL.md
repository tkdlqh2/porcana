---
name: api-specs
description: Porcana API endpoint specifications. Complete API contract for frontend integration. Use when implementing or testing API endpoints.
disable-model-invocation: false
---

# Porcana MVP API Specifications

## Base Configuration
- Base Path: `/api/v1`
- Auth: `Authorization: Bearer {accessToken}`
- Content-Type: `application/json`
- Date format: ISO-8601 (YYYY-MM-DD for chart points)
- Enum:
    - PortfolioStatus: DRAFT | ACTIVE | FINISHED

---

## 0) Guest Session (비회원 지원)

### POST /guest-sessions
**Description**: 비회원을 위한 게스트 세션을 생성합니다. 서버는 응답 본문과 `X-Guest-Session-Id` 헤더로 세션 ID를 반환합니다.

**Auth**: Not required

Request
```json
{}
```

Response (201 Created)
```json
{
  "guestSessionId": "uuid"
}
```

**Response Header:**
```text
X-Guest-Session-Id: {sessionId}
```

**Notes:**
- 프론트엔드는 응답의 `guestSessionId` 또는 `X-Guest-Session-Id` 헤더를 로컬스토리지 등에 저장
- 이후 API 요청 시 `X-Guest-Session-Id` 헤더로 전송
- 세션 유효기간: 30일 (`last_seen_at` 기준)

---

## 1) Auth / User

### POST /auth/signup
**Description**: 회원가입을 처리합니다. 요청에 `X-Guest-Session-Id` 헤더가 있으면 게스트 포트폴리오를 자동으로 사용자 계정으로 이전합니다.

**Auth**: Not required

**Guest Session Claim:**
- 서버는 요청의 `X-Guest-Session-Id` 헤더를 확인
- 게스트 세션이 있으면 해당 포트폴리오/아레나를 신규 사용자 계정으로 이전
- 메인 포트폴리오가 없으면 가장 최근 게스트 포트폴리오를 메인으로 설정

Request
```json
{
  "email": "string",
  "password": "string",
  "nickname": "string"
}
```

Response (200 OK)
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "user": {
    "userId": "uuid",
    "nickname": "string",
    "mainPortfolioId": "uuid|null"
  }
}
```

### GET /auth/check-email?email=string
Check if email is available for signup

Response
```json
{
  "available": true|false
}
```

### POST /auth/login
**Description**: 로그인을 처리합니다. 요청에 `X-Guest-Session-Id` 헤더가 있으면 게스트 포트폴리오를 자동으로 사용자 계정으로 이전합니다.

**Auth**: Not required

**지원 Provider**: EMAIL, GOOGLE, APPLE

**Guest Session Claim:**
- 서버는 요청의 `X-Guest-Session-Id` 헤더를 확인
- 게스트 세션이 있으면 해당 포트폴리오/아레나를 사용자 계정으로 이전 (merge)
- 기존 포트폴리오와 게스트 포트폴리오 모두 유지

Request (EMAIL provider)
```json
{
  "provider": "EMAIL",
  "email": "string",
  "password": "string"
}
```

Request (OAuth providers - GOOGLE, APPLE)
```json
{
  "provider": "GOOGLE|APPLE",
  "code": "string"
}
```

Response (200 OK)
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "user": {
    "userId": "uuid",
    "nickname": "string",
    "mainPortfolioId": "uuid|null"
  }
}
```

**Validation Notes:**
- EMAIL provider: email, password 필수
- GOOGLE/APPLE provider: code 필수 (OAuth authorization code)
- 커스텀 validator (@ValidLoginRequest)로 provider별 필수 필드 검증

### POST /auth/refresh
Request
```json
{
  "refreshToken": "string"
}
```

Response
```json
{
  "accessToken": "string",
  "refreshToken": "string"
}
```

### GET /me
**Description**: 현재 인증된 사용자의 최신 정보를 조회합니다. 로그인/회원가입 시 user 정보가 응답에 포함되지만, 이 API는 토큰으로 최신 유저 정보를 조회할 때 사용합니다.

**Auth**: Required (JWT)

Response
```json
{
  "userId": "uuid",
  "nickname": "string",
  "mainPortfolioId": "uuid|null"
}
```

### PATCH /me
**Auth**: Required (JWT)

Request
```json
{
  "nickname": "string"
}
```

Response
```json
{
  "userId": "uuid",
  "nickname": "string"
}
```

---

## 2) Home (Main Portfolio Widget)

### GET /home
Response (when no main portfolio)
```json
{
  "hasMainPortfolio": false
}
```

Response (when has main portfolio)
```json
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
```

### PUT /portfolios/{portfolioId}/main
**Description**: 지정한 포트폴리오를 메인 포트폴리오로 설정합니다. 다른 포트폴리오가 이미 메인으로 설정되어 있다면 변경됩니다.

Response
```json
{
  "mainPortfolioId": "uuid"
}
```

---

## 3) Portfolio List

### GET /portfolios
**Note**: DRAFT 상태의 포트폴리오는 리스트에 표시되지 않습니다. ACTIVE 및 FINISHED 상태만 조회됩니다.

Response
```json
[
  {
    "portfolioId": "uuid",
    "name": "string",
    "status": "ACTIVE|FINISHED",
    "isMain": true,
    "totalReturnPct": 12.34,
    "createdAt": "YYYY-MM-DD"
  }
]
```

---

## 4) Portfolio (CRUD minimal for MVP)

### POST /portfolios
**Description**: 새로운 포트폴리오를 생성합니다. 비회원도 생성 가능합니다.

**Auth**: Optional (JWT 또는 Guest Session)

**소유권 결정:**
- `Authorization` 헤더가 있으면 → 사용자 소유 (`user_id` 설정)
- 없으면 → 게스트 소유 (`guest_session_id` 설정)
- 게스트 세션이 없으면 서버가 자동 생성

**게스트 제한:**
- 게스트당 최대 3개 포트폴리오

Request
```json
{
  "name": "string"
}
```

Response (201 Created)
```json
{
  "portfolioId": "uuid",
  "name": "string",
  "status": "DRAFT",
  "createdAt": "YYYY-MM-DD"
}
```

### GET /portfolios/{portfolioId}
Response
```json
{
  "portfolioId": "uuid",
  "name": "string",
  "status": "DRAFT|ACTIVE|FINISHED",
  "isMain": true,
  "startedAt": "YYYY-MM-DD|null",
  "totalReturnPct": 12.34,
  "averageRiskLevel": 3.2,
  "diversityLevel": "HIGH",
  "riskDistribution": {
    "1": 10.0,
    "2": 20.0,
    "3": 30.0,
    "4": 25.0,
    "5": 15.0
  },
  "positions": [
    {
      "assetId": "uuid",
      "ticker": "string",
      "name": "string",
      "currentRiskLevel": 4,
      "weightPct": 25.0,
      "returnPct": 18.3
    }
  ]
}
```

**Portfolio-Level Risk Metrics:**
- `averageRiskLevel`: 가중 평균 위험도 (1.0 - 5.0, null 가능)
    - 각 자산의 currentRiskLevel × weightPct로 계산
    - 예: (4 × 0.25) + (3 × 0.25) + (2 × 0.50) = 2.75
- `diversityLevel`: 분산도 수준 ("HIGH" | "MEDIUM" | "LOW")
    - 섹터 다양성 (50%), 위험도 밴드 다양성 (30%), 자산 타입 다양성 (20%) 종합
    - HIGH: 70점 이상 (여러 섹터, 여러 위험도, 주식+ETF 혼합)
    - MEDIUM: 40-70점
    - LOW: 40점 미만 (단일 섹터, 단일 위험도, 단일 타입)
- `riskDistribution`: 위험도별 비중 분포 (Map<Integer, Double>)
    - Key: 위험도 레벨 (1-5)
    - Value: 해당 위험도 자산들의 비중 합계 (%)
    - 예: { "1": 10.0, "2": 20.0, "3": 30.0, "4": 25.0, "5": 15.0 }
    - 모든 레벨 (1-5)이 항상 포함되며, 없는 레벨은 0.0%
    - 합계는 100%가 되어야 함 (riskLevel이 null인 자산 제외)

### PATCH /portfolios/{portfolioId}/name
**Description**: 포트폴리오의 이름을 수정합니다.

**Auth**: Required (JWT)

Request
```json
{
  "name": "string"
}
```

Response (200 OK)
```json
{
  "portfolioId": "uuid",
  "name": "string"
}
```

Error Responses
- 400: 포트폴리오를 찾을 수 없거나 권한이 없음
- 401: 인증 필요

### DELETE /portfolios/{portfolioId}
**Description**: 포트폴리오를 삭제합니다. Soft delete로 처리되며, 30일 후 배치 작업에 의해 완전히 삭제됩니다.

**Auth**: Required (JWT 또는 Guest Session)

**소유권 검증:**
- 사용자 소유 포트폴리오: JWT 토큰의 userId와 일치해야 함
- 게스트 소유 포트폴리오: `X-Guest-Session-Id` 헤더와 일치해야 함

Response (204 No Content)
```
(empty body)
```

Error Responses
- 400: 포트폴리오를 찾을 수 없음
- 403: 포트폴리오 삭제 권한이 없음
- 401: 인증 필요

**Notes:**
- 삭제된 포트폴리오는 목록 조회에서 제외됨
- 삭제된 포트폴리오는 수정 불가
- 30일 보관 기간 후 `deletedPortfolioCleanupJob` 배치에 의해 완전 삭제

---

### PUT /portfolios/{portfolioId}/weights
**Description**: 포트폴리오 내 자산들의 비중을 일괄 수정합니다. 비중의 합계는 반드시 100%가 되어야 합니다.

**Auth**: Required (JWT)

**비중 수정 시 동작:**
1. `PortfolioAsset.weightPct` 업데이트 → 사용자가 설정한 비중 저장
2. 오늘 날짜로 `PortfolioSnapshot` 생성/업데이트 → 리밸런싱 이력 기록
   - 같은 날 여러 번 수정 가능 (기존 스냅샷 업데이트)
3. UI 즉시 반영:
   - 수정 직후: `PortfolioAsset.weightPct` 사용 (사용자가 설정한 비중)
   - 다음 날 배치 후: `SnapshotAssetDailyReturn.weightUsed` 사용 (시가총액 기반 비중)

Request
```json
{
  "weights": [
    {
      "assetId": "uuid",
      "weightPct": 30.0
    },
    {
      "assetId": "uuid",
      "weightPct": 70.0
    }
  ]
}
```

Response (200 OK)
```json
{
  "portfolioId": "uuid",
  "name": "string",
  "weights": [
    {
      "assetId": "uuid",
      "weightPct": 30.0
    },
    {
      "assetId": "uuid",
      "weightPct": 70.0
    }
  ]
}
```

Error Responses
- 400: 비중 합계가 100%가 아님, 포트폴리오를 찾을 수 없음, 자산이 포트폴리오에 없음
- 401: 인증 필요

**Validation Rules:**
- 비중 합계는 정확히 100.0% 이어야 함
- 모든 `assetId`는 포트폴리오에 존재하는 자산이어야 함
- `weightPct`는 0 이상 100 이하

---

## 5) Arena (Hearthstone-style drafting)

### POST /arena/sessions
**Description**: 포트폴리오에 대한 새로운 아레나 드래프트 세션을 시작합니다. 이미 진행 중인 세션이 있으면 해당 세션을 반환합니다. 비회원도 사용 가능합니다.

**Auth**: Optional (JWT 또는 Guest Session)

**소유권 결정:**
- `Authorization` 헤더가 있으면 → 사용자 소유 (`user_id` 설정)
- 없으면 → 게스트 소유 (`guest_session_id` 설정)
- 포트폴리오 소유권과 일치하는 세션만 생성 가능

Request
```json
{
  "portfolioId": "uuid"
}
```

Response (200 OK)
```json
{
  "sessionId": "uuid",
  "portfolioId": "uuid",
  "status": "IN_PROGRESS",
  "currentRound": 0
}
```

Error Responses
- 400: 포트폴리오를 찾을 수 없거나 권한이 없음
- 403: 포트폴리오 소유권이 일치하지 않음

### GET /arena/sessions/{sessionId}
**Description**: 진행 중이거나 완료된 아레나 세션의 상세 정보를 조회합니다.

**Auth**: Required (JWT)

Response (200 OK)
```json
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
```

Error Responses
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

### GET /arena/sessions/{sessionId}/rounds/current
**Description**: 현재 진행 중인 라운드의 선택지를 조회합니다.
- Round 0: 투자 성향 + 섹터 동시 선택 (Pre Round)
- Round 1-10: 자산 선택

**Auth**: Required (JWT)

Response for Round 0 (Pre Round - Risk Profile + Sector Selection)
```json
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
    }
  ],
  "minSectorSelection": 0,
  "maxSectorSelection": 3
}
```

**Field Notes:**
- `riskProfileOptions`: 투자 성향 선택지 (SAFE/BALANCED/AGGRESSIVE 중 1개 필수 선택)
- `sectorOptions`: 섹터 선택지 (0-3개 선택 가능)
- `value`: Sector enum 값
- `displayName`: 한국어 섹터명
- `assetCount`: 해당 섹터에 속한 활성 자산 개수

Response for Round 1-10 (Asset Selection)
```json
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
      "market": "US",
      "assetClass": null,
      "currentRiskLevel": 4,
      "imageUrl": "https://financialmodelingprep.com/image-stock/AAPL.png",
      "impactHint": "성장 비중 ↑ · 변동성 ↑"
    },
    {
      "assetId": "uuid",
      "ticker": "MSFT",
      "name": "Microsoft Corp.",
      "sector": "INFORMATION_TECHNOLOGY",
      "market": "US",
      "assetClass": null,
      "currentRiskLevel": 3,
      "imageUrl": "https://financialmodelingprep.com/image-stock/MSFT.png",
      "impactHint": "성장 비중 ↑ · 균형"
    },
    {
      "assetId": "uuid",
      "ticker": "SPY",
      "name": "SPDR S&P 500 ETF",
      "sector": null,
      "market": "US",
      "assetClass": "EQUITY_INDEX",
      "currentRiskLevel": 2,
      "imageUrl": "https://financialmodelingprep.com/image-stock/SPY.png",
      "impactHint": "분산 효과 · 균형"
    }
  ]
}
```

**Field Notes:**
- `sector`: 주식(STOCK)의 경우 GICS 섹터, ETF는 null
- `market`: 시장 구분 (KR | US)
- `assetClass`: ETF의 경우 자산 클래스 (EQUITY_INDEX, DIVIDEND, BOND 등), 주식은 null
- `currentRiskLevel`: 위험도 레벨 (1-5, 1: Low, 5: High, null 가능)
- `imageUrl`: 회사 로고 이미지 URL (미국 주식/ETF만 제공, 한국 종목은 null)
    - FMP API 제공: `https://financialmodelingprep.com/image-stock/{SYMBOL}.png`
- `impactHint`: 포트폴리오에 미치는 영향 힌트 (역할 · 리스크)
    - 역할: ETF는 assetClass 기반 ("분산 효과", "배당 기여", "방어 역할"), 주식은 sector 기반 ("성장 비중 ↑", "경기 민감", "방어적")
    - 리스크: currentRiskLevel 기반 ("변동성 ↑", "균형", "안정성 ↑")

Error Responses
- 400: 세션이 이미 완료됨
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

### POST /arena/sessions/{sessionId}/rounds/current/pick-preferences
**Description**: 아레나 Round 0 (Pre Round)에서 투자 성향(리스크 프로필)과 관심 섹터를 동시에 선택합니다. 0-3개의 섹터를 선택할 수 있으며, 중복은 허용되지 않습니다.

**Auth**: Required (JWT)

Request
```json
{
  "riskProfile": "SAFE|BALANCED|AGGRESSIVE",
  "sectors": ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"]
}
```

Response (200 OK)
```json
{
  "sessionId": "uuid",
  "status": "IN_PROGRESS",
  "currentRound": 1,
  "picked": {
    "riskProfile": "BALANCED",
    "sectors": ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"]
  }
}
```

Error Responses
- 400: Round 0이 아니거나 섹터 개수가 3개 초과 또는 중복된 섹터 포함
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

### POST /arena/sessions/{sessionId}/rounds/current/pick-asset
**Description**: 아레나 Round 1-10에서 제시된 3개의 자산 중 1개를 선택합니다. Round 10 완료 시 세션이 종료되고 포트폴리오가 완성됩니다.

**Auto Actions on Round 10 Completion:**
1. 포트폴리오에 선택된 10개 자산 추가 (균등 비중 10% 씩)
2. 포트폴리오 스냅샷 생성
3. **포트폴리오 자동 시작** (DRAFT → ACTIVE, startedAt = today)
4. **메인 포트폴리오 자동 설정** (사용자의 mainPortfolioId가 null인 경우)

**Auth**: Required (JWT)

Request
```json
{
  "pickedAssetId": "uuid"
}
```

Response (200 OK)
```json
{
  "sessionId": "uuid",
  "status": "IN_PROGRESS|COMPLETED",
  "currentRound": 2,
  "picked": "uuid"
}
```

Error Responses
- 400: Round 1-10이 아니거나 제시된 자산 목록에 없는 자산 선택
- 403: 세션을 찾을 수 없거나 권한이 없음
- 401: 인증 필요

---

## 6) Portfolio Performance

### GET /portfolios/{portfolioId}/performance?range=1M|3M|1Y
Response
```json
{
  "portfolioId": "uuid",
  "range": "1M",
  "points": [
    { "date": "YYYY-MM-DD", "value": 100.0 }
  ]
}
```

---

## 7) Assets (종목)

### GET /assets/search?query=string
Response
```json
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
```

### GET /assets/{assetId}
Response
```json
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
```

### GET /assets/{assetId}/chart?range=1M|3M|1Y
Response
```json
{
  "assetId": "uuid",
  "range": "1M",
  "points": [
    { "date": "YYYY-MM-DD", "price": 123.45 }
  ]
}
```

### GET /assets/{assetId}/in-my-main-portfolio
Response (not included)
```json
{
  "included": false
}
```

Response (included)
```json
{
  "included": true,
  "portfolioId": "uuid",
  "weightPct": 25.0,
  "returnPct": 18.3
}
```