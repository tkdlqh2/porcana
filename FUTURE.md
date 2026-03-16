# Porcana 미래 로드맵

## 완료된 기능

### Holding Baseline (Portfolio Management) ✅

> 상세 설계: `docs/HOLDING_BASELINE_PLAN.md` 참조

사용자가 시드 금액을 설정하면 각 종목별 매수 수량을 자동 계산하고, 추가 입금 시 어떤 종목을 매수해야 비중이 맞춰지는지 안내합니다.

| 구분 | 역할 | 설명 |
|------|------|------|
| **비중 (weight)** | 목표 | "난 AAPL을 25% 담고 싶어" |
| **Baseline** | 현실 | "지금 AAPL 8주 보유 중" |
| **수익률 계산** | 비중 기반 유지 | 기존 로직 변경 없음 |
| **리밸런싱 가이드** | Baseline 기반 | "비중 맞추려면 3주 더 사세요" |

#### 구현된 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| PUT | `/portfolios/{id}/seed` | 시드 금액 설정 (자동 수량 계산) |
| GET | `/portfolios/{id}/holding-baseline` | Baseline 조회 |
| POST | `/portfolios/{id}/top-up-plan` | 추가 입금 추천 (BUY only) |

---

## 향후 로드맵

### Phase 1: 학습 플랫폼 기반 (백테스팅 + 교육 콘텐츠)
- 과거 포트폴리오 성과 시뮬레이션
- 벤치마크 비교 (SPY, KOSPI200)
- 투자 교육 콘텐츠 통합

### Phase 2: 커뮤니티
- 포트폴리오 공유/좋아요/댓글
- 유저 팔로우 시스템
- 성과 리더보드

### Phase 3: 증권사 연동
- 한국투자증권 API 연동
- 보유 종목 자동 동기화
- 원클릭 리밸런싱 (딥링크)

---

**마지막 업데이트:** 2026-03-16
