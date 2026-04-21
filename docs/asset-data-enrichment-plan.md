# Asset Data Enrichment Plan

Last updated: 2026-04-21

## Goal

자산 상세 데이터의 빈 구간을 메우고, 미국/한국 종목 모두에 대해 운영 가능한 설명, 이미지, 배당 업데이트 흐름을 만든다.

## Completed (feat/asset-data-enrichment PR)

- US 종목 `description`, `isActivelyTrading` 반영 (FMP 연동)
- KR 상폐 종목 자동 deactivate (krAssetJob)
- KR 종목 배당 데이터 수집 (DART API 연동)
- Admin 배치 로그 (`AdminBatchJobRun`, `AdminBatchJobIssue`)

---

## Remaining Work

### 1. US Universe Expansion

목표:
- 미국 종목 수집 범위에 `AMEX`를 추가한다.

작업:
- FMP 미국 종목 수집이 `S&P500/NASDAQ100` CSV에만 의존하지 않도록 확장한다.
- `AMEX` 소스는 별도 심볼 리스트 또는 FMP 거래소 기반 조회로 붙인다.
- 유니버스 태그가 필요하면 `AMEX` 태그를 추가한다.

결정:
- 단기: 코드에서 `AMEX` 입력 경로를 먼저 열고 기존 흐름과 호환되게 유지한다.
- 중기: CSV 수동 관리 대신 FMP 거래소 기반 동기화로 전환 검토.

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

## Execution Order (Next PR)

1. US Universe Expansion (AMEX 입력 경로 추가)
2. KR 종목 설명 생성 배치 (템플릿 → AI 보강)
3. KR 종목 이미지 수집기 (캐시/검수 정책 포함)
4. 자산 상세 응답 구조화 카드 확장

## Open Questions

- AMEX를 "거래소 전체"로 볼지, 별도 큐레이션 리스트로 제한할지
- 한국 종목 설명 생성에 사용할 원천 데이터 범위를 어디까지 허용할지
- 이미지 외부 URL 직링크를 허용할지, 내부 저장소 캐시를 강제할지
- AI 생성 텍스트를 실시간으로 만들지, 배치로 선생성할지
