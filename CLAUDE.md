# Porcana MVP API Contract (Frontend 공유용)

## Base
- Base Path: /app/v1
- Auth: Authorization: Bearer {accessToken}
- Content-Type: application/json
- Date format: ISO-8601 (YYYY-MM-DD for chart points)
- Enum:
    - DeckStatus: DRAFT | ACTIVE | FINISHED

## Screen List (MVP)
1) Login
2) Home (main deck widget only)
3) Deck List
4) Deck Create Start
5) Arena Round (pick 1 of 3)
6) Deck Create Complete
7) Deck Detail
8) Deck Performance Chart (tab or separate)
9) Asset Detail

---

# 1) Auth / User

## POST /auth/login
Request
{
"provider": "GOOGLE|KAKAO|EMAIL",
"code": "string (optional)",
"email": "string (optional)",
"password": "string (optional)"
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
"mainDeckId": "uuid|null"
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

# 2) Home (Main Deck Widget)

## GET /home
Response (when no main deck)
{
"hasMainDeck": false
}

Response (when has main deck)
{
"hasMainDeck": true,
"mainDeck": {
"deckId": "uuid",
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

## PUT /decks/{deckId}/main
Response
{ "mainDeckId": "uuid" }

## DELETE /decks/main
Response
{ "mainDeckId": null }

---

# 3) Deck List

## GET /decks
Response
[
{
"deckId": "uuid",
"name": "string",
"status": "DRAFT|ACTIVE|FINISHED",
"isMain": true,
"totalReturnPct": 12.34,
"createdAt": "YYYY-MM-DD"
}
]

---

# 4) Deck (CRUD minimal for MVP)

## POST /decks
Request
{
"name": "string"
}
Response
{
"deckId": "uuid",
"name": "string",
"status": "DRAFT",
"createdAt": "YYYY-MM-DD"
}

## GET /decks/{deckId}
Response
{
"deckId": "uuid",
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

## POST /decks/{deckId}/start
Response
{
"deckId": "uuid",
"status": "ACTIVE",
"startedAt": "YYYY-MM-DD"
}

---

# 5) Arena (Hearthstone-style drafting)

## POST /arena/sessions
Request
{
"deckId": "uuid"
}
Response
{
"sessionId": "uuid",
"deckId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 1
}

## GET /arena/sessions/{sessionId}/rounds/current
Response
{
"sessionId": "uuid",
"round": 1,
"cards": [
{
"cardId": "uuid",
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
"pickedCardId": "uuid"
}
Response
{
"sessionId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 2,
"picked": {
"cardId": "uuid",
"assetId": "uuid"
}
}

## GET /arena/sessions/{sessionId}
Response
{
"sessionId": "uuid",
"deckId": "uuid",
"status": "IN_PROGRESS|COMPLETED",
"currentRound": 3,
"totalRounds": 10
}

---

# 6) Deck Performance

## GET /decks/{deckId}/performance?range=1M|3M|1Y
Response
{
"deckId": "uuid",
"range": "1M",
"points": [
{ "date": "YYYY-MM-DD", "value": 100.0 }
]
}

---

# 7) Assets (Stock cards)

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

## GET /assets/{assetId}/in-my-main-deck
Response (not included)
{ "included": false }

Response (included)
{
"included": true,
"deckId": "uuid",
"weightPct": 25.0,
"returnPct": 18.3
}
