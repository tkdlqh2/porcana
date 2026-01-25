## 0) 목표 구조 한 줄 요약

* 비회원은 `guest_sessions`로 “임시 소유권”을 가진다
* 회원가입/로그인 시 `guest_session_id → user_id`로 **소유권 이전(claim)** 한다

---

## 1) DB 변경 (Flyway SQL로 그대로 들어가게)

### 1-1. `guest_sessions` 테이블 추가

```sql
CREATE TABLE guest_sessions (
  id UUID PRIMARY KEY,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### 1-2. `portfolios`에 `guest_session_id` 추가 + `user_id` nullable로 변경

```sql
ALTER TABLE portfolios
  ADD COLUMN guest_session_id UUID NULL;

ALTER TABLE portfolios
  ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE portfolios
  ADD CONSTRAINT fk_portfolios_guest_session
  FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id);

-- 핵심: 둘 중 하나만 존재해야 함
ALTER TABLE portfolios
  ADD CONSTRAINT ck_portfolios_owner_xor
  CHECK (
    (user_id IS NULL AND guest_session_id IS NOT NULL)
    OR
    (user_id IS NOT NULL AND guest_session_id IS NULL)
  );

-- 인덱스
CREATE INDEX idx_portfolios_guest_session_id ON portfolios(guest_session_id);
```

### 1-3. `arena_sessions`도 동일하게 게스트 지원

ERD에 `arena_sessions.user_id NOT NULL` 이라 비회원 투기장이 불가능해짐.

```sql
ALTER TABLE arena_sessions
  ADD COLUMN guest_session_id UUID NULL;

ALTER TABLE arena_sessions
  ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE arena_sessions
  ADD CONSTRAINT fk_arena_sessions_guest_session
  FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id);

ALTER TABLE arena_sessions
  ADD CONSTRAINT ck_arena_sessions_owner_xor
  CHECK (
    (user_id IS NULL AND guest_session_id IS NOT NULL)
    OR
    (user_id IS NOT NULL AND guest_session_id IS NULL)
  );

CREATE INDEX idx_arena_sessions_guest_session_id ON arena_sessions(guest_session_id);
```

> ✅ 결론: **portfolios + arena_sessions** 둘 다 “user 또는 guest” 소유가 가능해져야 흐름이 끊기지 않아.

---

## 2) API 설계 (딱 MVP에 필요한 만큼)

### 2-1. 게스트 세션 발급 (서버가 쿠키 세팅)

* `POST /guest-sessions`

    * 응답: `{ guestSessionId: "..." }` (프론트 디버깅용으로만)
    * 서버: `Set-Cookie: porcana_guest=...; HttpOnly; SameSite=Lax; Path=/; Max-Age=...`

> 프론트는 “없는 경우에만 호출”하면 됨.

### 2-2. 게스트로 포트폴리오 생성

* `POST /portfolios`

    * 로그인 토큰 있으면 → user 소유로 생성
    * 없으면 → **쿠키의 guestSession로 생성**
    * (없으면 서버가 자동으로 guestSession 생성해도 됨)

### 2-3. 회원가입/로그인 시 “가져오기(claim)”

* `POST /auth/signup` (또는 `/auth/login`)

    * 서버는 **요청에서 guestSession 쿠키 확인**
    * 있으면 `claimGuestPortfolios(guestSessionId, userId)` 수행

---

## 3) 서버 로직 (중요한 부분만 정확히)

### 3-1. Request 들어올 때 게스트 세션 확보

* 인증 필터/인터셉터에서:

    * `Authorization` 있으면 user 컨텍스트
    * 없으면 `porcana_guest` 쿠키 확인

        * 없으면 새로 발급 + 쿠키 세팅

### 3-2. 포트폴리오 생성 서비스

* user면: `user_id = userId, guest_session_id = null`
* guest면: `user_id = null, guest_session_id = guestId`

### 3-3. Claim(소유권 이전) 트랜잭션

**회원가입/로그인 직후 1번만 호출되게 하면 됨.**

```java
@Transactional
public void claim(UUID guestSessionId, UUID userId) {
  // 1) 게스트 포트폴리오 조회
  List<Portfolio> guestPortfolios =
      portfolioRepo.findByGuestSessionIdForUpdate(guestSessionId); // SELECT ... FOR UPDATE 추천

  if (guestPortfolios.isEmpty()) return; // 멱등

  // 2) 소유권 이전
  for (Portfolio p : guestPortfolios) {
    p.claimToUser(userId); // userId 세팅 + guestSessionId null
  }

  // 3) 메인 포트폴리오가 없으면 하나 지정 (규칙 고정)
  User user = userRepo.findByIdForUpdate(userId);
  if (user.getMainPortfolioId() == null) {
     UUID newest = guestPortfolios.stream()
        .max(Comparator.comparing(Portfolio::getCreatedAt))
        .get().getId();
     user.setMainPortfolioId(newest);
  }
}
```

> 포인트: **FOR UPDATE** 걸어야 “같은 guestSession을 두 번 클레임” 같은 이상 케이스에서 깔끔하게 막힘.

---

## 4) 프론트 최소 변경

* 앱 시작 시:

    * `porcana_guest` 쿠키 없으면 `POST /guest-sessions`
* 이후 포트폴리오/투기장 API는 그냥 호출
* 회원가입/로그인 성공하면:

    * 서버가 알아서 claim 했으니 **그냥 내 포트폴리오 목록 다시 조회**하면 끝

---

## 5) 딱 3가지 규칙만 정해라 (안 정하면 UX가 흔들림)

1. **게스트 덱은 몇 개까지 허용?**

    * MVP 추천: 1~3개 제한(안 그러면 DB도 지저분해짐)

2. **회원가입 시 내 계정에 이미 덱이 있으면 합칠지/대체할지**

    * MVP 추천: “그냥 추가(merge)” + 메인은 “이미 있으면 유지, 없으면 게스트 최신”

3. **게스트 세션 만료 정책**

    * MVP 추천: 30일. (삭제 배치로 `last_seen_at` 기준 정리)