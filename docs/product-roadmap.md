# Product Roadmap

Last updated: 2026-04-29

## Goal

자산 데이터 강화부터 포트폴리오 분석, 공유, AI 리포트까지 단계별 기능 확장 계획을 정리한다.

## Completed (feat/asset-data-enrichment PR)

- US 종목 `description`, `isActivelyTrading` 반영 (FMP 연동)
- KR 상폐 종목 자동 deactivate (krAssetJob)
- KR 종목 배당 데이터 수집 (DART API 연동)
- Admin 배치 로그 (`AdminBatchJobRun`, `AdminBatchJobIssue`)

## Completed (feat/us-asset-universe-sync PR #38)

**US Universe Expansion**

구현된 아키텍처:
- **종목 리스트 동기화** (`UsUniverseSyncJob`, 월 1회 - 매월 첫째 일요일 01:00 KST)
  - S&P 500 / NASDAQ 100: Wikipedia 크롤링 (jsoup) — 분기/연간 리밸런싱 자동 반영
  - Dow Jones 30: `batch/dowjones.csv` 수동 관리 — 10년에 수차례만 변경되므로 CSV가 더 안정적
  - 신규 심볼은 `active=false`로 추가, 기존 심볼은 유니버스 태그만 merge (추가/제거)
- **종목 활성 상태 체크** (`UsAssetBatchJob`, 주 1회 - 매주 일요일 02:00 KST)
  - DB의 모든 US STOCK에 대해 FMP profile API 호출 → `activelyTrading` 반영
  - `imageUrl`, `description` 메타데이터 업데이트
  - 비활성화된 종목이 담긴 ACTIVE 포트폴리오 자동 FINISHED 처리
- **ETF 관리**: `batch/us_etf.csv` 큐레이션 리스트 유지 (VOO, QQQ, SCHD, VIG, XBI 등 43개)
  - AMEX 상장 ETF (SCHD, VIG, XBI 등) 포함
  - ETF는 FMP profile API 없이 CSV → DB upsert 방식 유지

결정:
- AMEX를 "거래소 전체"가 아닌 큐레이션 리스트로 제한 → `us_etf.csv`에 포함하는 방식 채택
- Wikipedia 파싱 실패 시 빈 Set 반환하여 데이터 손실 방지 (skip 정책)
- Dow Jones Wikipedia 파싱은 HTML 구조 불안정으로 제외, CSV 방식 유지

---

## Completed (feat/email-auth PR #41)

**Auth & Email Foundation**

구현된 항목:
- `spring-boot-starter-mail` 기반 메일 발송 인프라와 `@Async` 실행기 구성
- 회원가입 후 이메일 인증 토큰 발급/검증/재발송 API
  - `GET /auth/verify-email?token=...`
  - `POST /auth/resend-verification`
- 비밀번호 재설정 흐름
  - `POST /auth/forgot-password`
  - `POST /auth/reset-password` with body(`token`, `newPassword`)
- 로그인 상태 비밀번호 변경 API
  - `PATCH /me/password`
- `GET /me` 응답 확장
  - `email`, `provider`, `emailVerified` 포함
- `email_verification_tokens`, `password_reset_tokens` 마이그레이션 추가
- Auth/User/E2E 테스트 및 CI 부팅 이슈 정리

남은 운영 보완:
- 프로덕션 메일 도메인/DNS/SPF/DKIM/DMARC 최종 연결
- 인증 메일/재설정 메일 재발송 rate limiting
- 메일 발송 실패 재시도 및 운영 알림
- 미인증 사용자 접근 정책 확정

---

## Phase 1.5: Pre-Launch Operational Foundation

Phase 2 진입 전에 처리해야 할 운영/계정 기반 작업. 현재 이메일 인증과 비밀번호 재설정의 백엔드 뼈대는 구현되었고, 아래는 남은 운영/제품 정리 항목이다.

### A. 메일 인프라 운영 전환

목표:
- 개발 환경 구현을 실제 운영 도메인 메일로 전환
- 인증/재설정/문의 답신 메일 전달률 안정화

남은 작업:
- **DNS**: Cloudflare 기준 SPF / DKIM / DMARC 최종 반영
- **발신**: Resend 운영 도메인 인증 및 `APP_FRONTEND_URL`/`APP_BASE_URL` 배포 환경값 정리
- **수신**: 운영 문의 수신 주소 포워딩 구성 (`noreply@porcana.io` 또는 별도 support 주소)
- **관측성**: 메일 발송 실패 로그, 재시도 정책, 운영 알림 연계

### B. 계정 보안/복구 후속 보강

목표:
- EMAIL provider 사용자 계정의 안정성과 운영 정책 보완

추가 작업:
- 인증 메일 재발송 / 비밀번호 재설정 요청 rate limiting
- 토큰 재발급 경쟁 조건 완화 정책 검토
- 미인증 사용자 차단 범위 결정
- 메일 템플릿 문구/브랜딩 다듬기

### C. 문의(Inquiry) API

목표:
- 사용자가 앱 내에서 문의를 작성하고, 운영자가 어드민에서 처리/답신

데이터 모델:
- `inquiry`
  - id, userId(nullable), guestSessionId(nullable), email, category, title, content, status (RECEIVED/IN_PROGRESS/RESOLVED), createdAt, respondedAt
- `inquiry_response`
  - inquiryId, responderId, content, sentAt

API (사용자):
- `POST /inquiries` — 비회원 가능 (email 필수)
- `GET /me/inquiries` — 회원 본인 문의 내역

API (어드민):
- `GET /admin/inquiries`
- `GET /admin/inquiries/{id}`
- `POST /admin/inquiries/{id}/responses`
- `PATCH /admin/inquiries/{id}/status`

### D. 약관 / 개인정보 처리방침 관리

목표:
- 법적 필수 문서를 버전 관리하며 노출

데이터 모델:
- `legal_document`
  - id, type (PRIVACY_POLICY / TERMS_OF_SERVICE / MARKETING_CONSENT), version, content (markdown), publishedAt, effectiveAt
- `user_consent`
  - userId, documentType, documentVersion, consentedAt

API:
- 공개: `GET /legal/{type}`
- 어드민: `GET /admin/legal/{type}/versions`, `POST /admin/legal/{type}`

### E. 온보딩 가이드 이미지

목표:
- 신규 사용자에게 Arena 흐름과 앱 사용 방식을 초기 가이드로 제공

작업 항목:
- 디자인 에셋 준비
- 정적 자원 저장소 결정 (Cloudflare R2 또는 S3)
- 어드민 또는 정적 설정으로 노출 순서 관리

데이터 모델 후보:
- `onboarding_slide`
  - id, imageUrl, title, description, displayOrder, active

API 후보:
- `GET /onboarding/slides`
- `GET /admin/onboarding/slides`, `POST`, `PATCH`

### 실행 순서 (Phase 1.5 내부)

1. 메일 인프라 운영 전환
2. 계정 보안/복구 후속 보강
3. 약관 / 개인정보 처리방침 관리
4. 문의 API
5. 온보딩 가이드 이미지

---

## Remaining Work

### 2. KR Description Strategy

목표:
- 한국 종목별 설명을 빈 값이 아니라 읽을 만한 텍스트로 채운다.

권장 전략:
- 1차 소스: 종목 메타데이터 기반 템플릿 설명
  - 회사명, 시장(KOSPI/KOSDAQ), 섹터
  - 시가총액/대표 제품/사업 키워드가 확보되면 포함
- 2차 소스: AI 보강
  - 입력: 회사명, 종목코드, 섹터, 최근 배당 여부, ETF 여부, 주요 지수 편입 여부
  - 출력: 2~4문장 한국어 설명
  - 저장: 배치 결과를 `description`에 반영
- 3차 운영 보정
  - 중요 종목만 어드민에서 수동 수정

비권장:
- 미국처럼 FMP `description` 번역만 기대하는 방식
  - 한국 종목은 원천 설명 자체가 빈약하거나 일관되지 않을 가능성이 높다.

### 3. KR Image Strategy

목표:
- 한국 종목 이미지 부재를 자동 보완한다.

권장 전략:
- 1차: 공식 IR/CI 이미지 URL 수집
  - 회사 홈페이지, 전자공시, 공공 데이터 등 신뢰 가능한 출처 우선
- 2차: 검색 엔진/포털 기반 크롤링으로 후보 수집
  - 네이버 검색 결과를 직접 서비스 런타임에서 긁는 방식은 차단/정책 리스크가 크다.
  - 별도 오프라인 수집 스크립트 또는 관리용 배치로 운용하는 편이 안전하다.
- 3차: 이미지 캐시/검수
  - 외부 URL을 바로 노출하지 말고 저장소 또는 별도 스토리지에 캐시하는 방향 검토
  - `defaultImage`, 너무 작은 이미지, 깨진 링크를 걸러야 한다.

권장 구현 방향:
- `KrAssetImageProvider`를 별도로 둔다.
- `KrImageUpdateRunner` 또는 배치 Job으로 누락 종목만 채운다.
- 수집 결과는 `asset.imageUrl`에 upsert 하고, 실패 종목은 재시도 목록으로 남긴다.

주의:
- 네이버/증권사 페이지 직접 크롤링은 robots, 차단, URL 구조 변경 리스크가 크다.
- 운영용이면 "수집 전용 스크립트 + 결과 검수 + 캐시 저장"이 런타임 직접 크롤링보다 낫다.

### 4. Description Beyond Plain Text

목표:
- 설명 텍스트만으로 부족한 종목 정보를 보완한다.

추가 후보:
- 한 줄 투자 포지션 (예: `반도체 대형주`, `배당형 ETF`, `미국 소비재 방어주`)
- 핵심 포인트 3개: 사업/섹터, 배당 성향, 편입 지수 또는 자산 성격
- AI 요약 카드: "이 종목은 어떤 역할인가?", "포트폴리오에 넣는 이유", "주의할 점"

권장 방식:
- 구조화 데이터와 생성 텍스트를 분리한다.
- `description` 하나에 모든 걸 우겨 넣지 말고, 장기적으로는 아래 필드를 검토한다.
  - `summary`, `bullPoints`, `riskNotes`, `businessTags`

---

## Phase 2: Portfolio Analytics

### 5. Portfolio Scoring & Metrics

목표:
- 포트폴리오 상세 화면에 단순 수익률 외 분석 지표를 추가한다.

추가 지표 후보:
- **기간별 평균 수익률**: 1M / 3M / 6M / 1Y 구간별 누적 수익률 평균
- **유지 기간**: 포트폴리오 ACTIVE 전환일 기준 경과 일수
- **안정성 스코어**: 일간 수익률 표준편차 기반 변동성 점수 (1~5, assetRiskJob과 유사한 방식)
- **종합 점수**: 수익률 + 안정성 조합 (샤프 비율 느낌)

구현 방향:
- `DailyReturn` 데이터가 이미 쌓이므로 계산 로직은 배치로 선계산 → `PortfolioSnapshot` 또는 별도 필드에 저장
- API: `GET /portfolios/{portfolioId}` 응답에 `analytics` 필드 추가, 또는 별도 엔드포인트

Open Questions:
- 점수 계산 기준을 수익률 단독으로 할지, 위험 조정 수익률(샤프)로 할지
- 선계산 배치 주기: 일일 가격 업데이트 직후(`portfolioPerformanceJob` 이후) 실행이 자연스러움

---

## Phase 3: Portfolio Sharing

### 6. Portfolio Share & Copy

목표:
- 포트폴리오를 짧은 코드/링크로 공유하고, 한 번에 복사해 내 포트폴리오로 만들 수 있다.
- 한 줄 코멘트로 가볍게 의도를 남기고, 인기 포트폴리오에는 댓글/토론 기능을 붙인다.

배경 결정:
- 토론방(포트폴리오 토론방)은 커뮤니티 측면에서 유리하지만, 초기에 빈 공간이 두드러지면 역효과
- 절충안: **한 줄 코멘트 먼저** → 글 쓰는 부담 없고, 피드/리포트에 바로 활용 가능
- 댓글/토론은 조회수·복사수가 쌓인 포트폴리오에만 추후 붙이는 방식

구현 방향:
- 공유 코드 생성: 짧은 UUID(8자) 또는 slug → `portfolio.shareCode` 필드 추가
- **한 줄 코멘트** (선택 입력): `portfolio.shareComment` — 예: "배당 중심으로 안정적으로 굴리는 포트폴리오"
- 공유 링크 조회: `GET /portfolios/shared/{shareCode}` — 비회원(게스트)도 접근 가능
- 복사: `POST /portfolios/shared/{shareCode}/copy` → DRAFT 상태 포트폴리오 생성
  - 종목/비중만 복사, 시드 금액은 새로 설정하게 유도
- 공유 여부는 포트폴리오 소유자가 toggle 가능 (`PUT /portfolios/{portfolioId}/share`)
- **Phase 4 연계**: 공유 포트폴리오의 코멘트를 AI 리포트 "이달의 추천" 텍스트 소스로 활용

비회원 흐름:
- 게스트 세션 있으면 게스트 포트폴리오로 복사 → 나중에 회원가입 시 claim
- 기존 게스트 세션 흐름과 자연스럽게 연결됨

미래 확장 (조회수/복사수 충분히 쌓인 후):
- 인기 포트폴리오 한정 댓글 기능
- 포트폴리오 토론방(포트폴리오별 스레드)

---

## Phase 4: AI Report

### 7. Portfolio Meta Report (AI)

목표:
- 카드게임 메타 리포트처럼 포트폴리오 트렌드, 인기 종목, 수익률 패턴을 자동 생성한다.

구현 방향:
- **데이터 수집 선행**: 실제 유저 포트폴리오가 쌓이기 전까지 Arena 시뮬레이션으로 샘플 포트폴리오 대량 생성
  - 랜덤 리스크 프로필 + 섹터 조합으로 수백 개 자동 생성하는 배치 Job
- **리포트 집계 배치** (월 1회):
  - 이번 달 가장 많이 선택된 종목 Top 10
  - 리스크 프로필별 평균 수익률
  - 섹터별 안정성 비교
  - 가장 수익률 높은 포트폴리오 구성 패턴
- **AI 문장 생성**: 집계 결과를 입력으로 리포트 텍스트 생성 (배치 선생성, 실시간 아님)
- **노출**: 홈 화면 또는 별도 리포트 탭

Open Questions:
- 리포트를 공개(모든 유저)로 볼지, 로그인 유저만 볼지
- AI 생성 텍스트 검수 프로세스: 자동 발행 vs 어드민 승인 후 발행

---

## Execution Order

| Phase | 단계 | 내용 |
|-------|------|------|
| 1 | Asset Enrichment | KR 설명 생성 → KR 이미지 → 종목 상세 카드 구조화 |
| 1.5 | Pre-Launch Foundation | 메일 운영 전환 → 계정 보안/복구 보강 → 약관/개인정보 → 문의 API → 온보딩 가이드 |
| 2 | Portfolio Analytics | 포트폴리오 점수 + 기간별 지표 |
| 3 | Portfolio Sharing | 공유 코드 + 복사 기능 |
| 4 | AI Report | 메타 리포트 배치 + AI 문장 생성 |

## Open Questions (Cross-cutting)

- 한국 종목 설명 생성에 사용할 원천 데이터 범위를 어디까지 허용할지
- 이미지 외부 URL 직링크를 허용할지, 내부 저장소 캐시를 강제할지
- AI 생성 텍스트를 실시간으로 만들지, 배치로 선생성할지 (현재 방향: 배치 선생성)
