# Porcana 미래 로드맵

## 현재 진행: Holding Baseline (Portfolio Management)

> 상세 설계: `docs/HOLDING_BASELINE_PLAN.md` 참조

### 개요

사용자가 "실제로 이만큼 보유하고 있다"고 확정한 스냅샷을 기반으로 리밸런싱 가이드를 제공합니다.

| 구분 | 역할 | 설명 |
|------|------|------|
| **비중 (weight)** | 목표 | "난 AAPL을 25% 담고 싶어" |
| **Baseline** | 현실 | "지금 AAPL 8주 보유 중" |
| **수익률 계산** | 비중 기반 유지 | 기존 로직 변경 없음 |
| **리밸런싱 가이드** | Baseline 기반 | "비중 맞추려면 3주 더 사세요" |

### API 엔드포인트

| Method | Endpoint | 설명 |
|--------|----------|------|
| PUT | `/portfolios/{id}/holding-baseline` | Baseline 생성/수정 |
| GET | `/portfolios/{id}/holding-baseline` | Baseline 조회 |
| POST | `/portfolios/{id}/holding-baseline/draft-from-seed` | 시드 기반 초안 |
| POST | `/portfolios/{id}/holding-baseline/confirm-seeded` | 시드 초안 확정 |
| GET | `/portfolios/{id}/rebalance-status` | 리밸런싱 상태 조회 |
| POST | `/portfolios/{id}/top-up-plan` | 추가 입금 추천 (BUY only) |
| POST | `/portfolios/{id}/rebalancing-plan` | 전체 리밸런싱 플랜 |

### 구현 순서

- [x] Phase 0: 직접 포트폴리오 생성 (완료)
- [ ] Phase 1: Entity + Repository
- [ ] Phase 2: Baseline CRUD
- [ ] Phase 3: 시드 기반 계산
- [ ] Phase 4: 리밸런싱 서비스
- [ ] Phase 5: 테스트 + 문서화

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
