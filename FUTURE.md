# Porcana 미래 로드맵

## 🎯 비전

**"투자 학습 플랫폼"**

초보 투자자가 재미있게 포트폴리오를 만들고, 과거 성과를 확인하고, 다른 사람들과 투자 아이디어를 나누며, 실제 계좌에서 쉽게 실행할 수 있는 올인원 플랫폼.

### 핵심 가치
1. **교육 중심**: 매매 권유가 아닌 학습 도구
2. **투명성**: 모든 수익률, 위험도 계산 과정 공개
3. **커뮤니티**: 혼자가 아닌 함께 배우는 투자
4. **실행 가능**: 학습에서 실전으로 자연스럽게 연결

---

## 📊 현재 상태 평가

### 기술 평가: 7.5/10

**강점:**
- ✅ 깔끔한 아키텍처 (Command Pattern, 계층화)
- ✅ 견고한 데이터 관리 (Flyway, Spring Batch)
- ✅ 최적화된 아레나 알고리즘 (가중치 샘플링, 버킷 최적화)
- ✅ 위험도 계산 (퍼센타일 기반, 통합 스케일)
- ✅ 포괄적인 문서화 (CLAUDE.md)

**개선 필요 영역:**
- ⚠️ N+1 쿼리 가능성 (@EntityGraph 필요)
- ⚠️ 캐싱 레이어 없음 (종목 데이터, 환율)
- ⚠️ 복잡한 로직에 대한 단위 테스트 부족
- ⚠️ 커스텀 예외 계층 구조 필요
- ⚠️ 보안 강화 필요 (rate limiting, secret 관리)

### 서비스 평가: 6.5/10

**강점:**
- ✅ 독특한 UX (Arena 드래프팅)
- ✅ 실제 시장 데이터 (S&P 500, KOSPI200)
- ✅ 정량적 위험도 지표

**약점:**
- ⚠️ 불분명한 가치 제안 ("왜 이걸 써야 하지?")
- ⚠️ 수익 모델 없음
- ⚠️ 리텐션 기능 부재
- ⚠️ 기존 앱과의 차별성 부족 (토스, 핀트)

---

## 🗺️ 로드맵

### Phase 0: 직접 포트폴리오 생성 (1주)
**목표: 사용자가 카드 라이브러리에서 원하는 종목을 직접 선택해서 포트폴리오 구성**

#### 포트폴리오 생성 방식 2가지

**1. Arena(투기장) 방식** - 기존
- 라운드마다 추천된 3개 중 1개 선택
- 추천 알고리즘 신뢰
- 재미있고 교육적

**2. 직접 생성 방식** - NEW (Phase 0)
- 카드 라이브러리에서 원하는 종목 직접 선택
- DB에 있는 모든 종목 탐색 가능 (S&P500, KOSPI200, KOSDAQ150, ETF 등)
- 빠르고 자유로운 포트폴리오 구성

#### 기능: 카드 라이브러리에서 직접 선택 (직접 생성 방식)
**우선순위: HIGH**

```
기능: 사용자가 카드 라이브러리에서 종목을 검색/필터링하고 포트폴리오에 추가

플로우:
1. "포트폴리오 직접 만들기" 선택
2. 포트폴리오 이름 입력
3. 카드 라이브러리 화면:
   - 필터: 시장(US/KR), 타입(주식/ETF), 섹터, 위험도
   - 검색: symbol 또는 name으로 DB 내 검색
   - 정렬: 이름순, 위험도순, 최근 추가순
4. 종목 카드 클릭 → 포트폴리오에 추가
5. 원하는 만큼 종목 추가 (최소 3개, 최대 20개)
6. 각 종목별 비중 설정 (균등 또는 수동)
7. 포트폴리오 생성 완료 (자동으로 ACTIVE 상태)

API:
- POST /portfolios/direct
  Request: { name: "내 포트폴리오" }
  Response: {
    portfolioId: "uuid",
    name: "내 포트폴리오",
    status: "DRAFT"
  }

- GET /assets/library?market={US|KR}&type={STOCK|ETF}&sector={...}&riskLevel={1-5}&q={query}
  Response: {
    assets: [
      {
        assetId: "uuid",
        symbol: "AAPL",
        name: "Apple Inc.",
        market: "US",
        type: "STOCK",
        sector: "INFORMATION_TECHNOLOGY",
        currentRiskLevel: 3,
        imageUrl: "https://..."
      },
      ...
    ],
    total: 500
  }

- POST /portfolios/{portfolioId}/assets
  Request: {
    assetId: "uuid",
    weightPct: 10.0  // optional, 생략시 균등 배분
  }
  Response: {
    portfolioId: "uuid",
    asset: {
      assetId: "uuid",
      symbol: "AAPL",
      name: "Apple Inc.",
      currentRiskLevel: 3
    },
    weightPct: 10.0
  }

- DELETE /portfolios/{portfolioId}/assets/{assetId}
  Response: { success: true }

- POST /portfolios/{portfolioId}/finalize
  Request: {
    rebalanceWeights: true  // 자동으로 100% 맞추기
  }
  Response: {
    portfolioId: "uuid",
    status: "ACTIVE",
    startedAt: "2025-01-21",
    assets: [
      { assetId: "uuid", symbol: "AAPL", weightPct: 10.0 },
      ...
    ]
  }
```

**기술 작업:**
1. 카드 라이브러리 API 구현 (필터링, 검색, 페이지네이션)
2. 직접 포트폴리오 생성 플로우 구현
3. 포트폴리오에 종목 추가/삭제 API
4. 비중 설정 및 검증 로직 (합계 100% 체크)
5. 프론트엔드: 카드 라이브러리 UI, 비중 설정 UI

**사용자 가치:**
- 원하는 종목으로 자유롭게 포트폴리오 구성
- Arena보다 빠른 포트폴리오 생성
- 전체 종목 풀(~1000개) 탐색 가능

---

### Phase 1: 학습 플랫폼 기반 구축 (2개월)
**목표: 핵심 교육 가치 확립**

#### 1-2주차: 백테스팅 MVP
**우선순위: HIGH**

```
기능: 과거 포트폴리오 성과 시뮬레이션

API:
- POST /portfolios/{id}/backtest
  Request: { startDate, endDate, initialAmount }
  Response: {
    totalReturn, cagr, mdd, sharpeRatio,
    vsSpx, vsKospi,
    dailyChart: [{ date, value }]
  }

구현:
- 기존 asset_prices 테이블 활용 (1년 과거 데이터)
- PortfolioReturnCalculator 활용
- 벤치마크 비교 추가 (미국: SPY, 한국: KOSPI200 ETF)
```

**기술 작업:**
1. `BacktestService` 생성
2. 지표 계산 구현 (CAGR, MDD, Sharpe Ratio)
3. 벤치마크 데이터 추가 (SPY, KOSPI200 ETF)
4. API 엔드포인트 + 테스트 작성
5. CLAUDE.md 업데이트

**사용자 가치:**
- "1년 전에 이렇게 투자했으면 +25% 수익!"
- 시장 벤치마크와 비교
- 포트폴리오 위험/수익 프로필 이해

---

#### 3-4주차: 교육 콘텐츠
**우선순위: MEDIUM**

```
기능: UX에 투자 교육 통합

1. Arena 종목 선택:
   - "왜 이 종목?" 설명 추가
   - 섹터 설명 표시
   - 위험도 레벨 의미 설명

2. 포트폴리오 상세:
   - 지표에 툴팁 추가 (MDD, Sharpe Ratio)
   - 섹터 다양성 가이드 제공
   - 위험도 레벨 해석

3. 용어집 페이지:
   - 투자 용어 (MDD, CAGR, Sharpe Ratio)
   - 자산 클래스 (주식, ETF)
   - 위험도 레벨 (1-5 스케일 설명)
```

**기술 작업:**
1. 응답에 `educationalContent` 필드 추가
2. 용어집 테이블/정적 콘텐츠 생성
3. 프론트엔드에 툴팁 표시 업데이트
4. "더 알아보기" 링크 추가

---

#### 5-8주차: 커뮤니티 기반
**우선순위: HIGH**

```sql
-- 데이터베이스 스키마

CREATE TABLE portfolio_shares (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT true,
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_portfolio_shares_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_shares_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_shares_public ON portfolio_shares(is_public, created_at DESC);
CREATE INDEX idx_portfolio_shares_user ON portfolio_shares(user_id);

CREATE TABLE portfolio_likes (
    user_id UUID NOT NULL,
    portfolio_share_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, portfolio_share_id),
    CONSTRAINT fk_portfolio_likes_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_likes_share FOREIGN KEY (portfolio_share_id)
        REFERENCES portfolio_shares(id) ON DELETE CASCADE
);

CREATE TABLE portfolio_comments (
    id UUID PRIMARY KEY,
    portfolio_share_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    parent_comment_id UUID NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_portfolio_comments_share FOREIGN KEY (portfolio_share_id)
        REFERENCES portfolio_shares(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_comments_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_comments_parent FOREIGN KEY (parent_comment_id)
        REFERENCES portfolio_comments(id) ON DELETE CASCADE
);

CREATE INDEX idx_portfolio_comments_share ON portfolio_comments(portfolio_share_id, created_at DESC);
```

**API 엔드포인트:**
```
POST   /portfolios/{id}/share         - 포트폴리오 공개 공유
DELETE /portfolios/{id}/share         - 공유 취소
GET    /community/portfolios           - 공개 포트폴리오 피드 (페이지네이션)
GET    /community/portfolios/{id}      - 공유된 포트폴리오 상세
POST   /community/portfolios/{id}/like - 포트폴리오 좋아요
DELETE /community/portfolios/{id}/like - 좋아요 취소
POST   /community/portfolios/{id}/comments - 댓글 작성
GET    /community/portfolios/{id}/comments - 댓글 조회
GET    /users/{id}/shared-portfolios  - 사용자의 공유 포트폴리오
```

**기술 작업:**
1. 엔티티 생성 (PortfolioShare, PortfolioLike, PortfolioComment)
2. 서비스 + 리포지토리 구현
3. 페이지네이션 추가 (page, size, sort)
4. API 컨트롤러 + 테스트 작성
5. 권한 부여 추가 (소유자만 수정/삭제)

**사용자 가치:**
- 다른 투자자의 전략 발견
- 성공적인 포트폴리오에서 학습
- 소셜 검증 + 참여

---

**Phase 1 성공 지표:**
- DAU: 100명
- 주간 활성률: 40%
- 백테스트 사용: 사용자의 50%
- 포트폴리오 공유: 사용자의 20%
- 댓글: 하루 10개

---

### Phase 2: 실전 연결 (3개월)
**목표: 학습을 실전으로 연결**

#### 3개월차: 증권사 API 연동
**우선순위: HIGH**

**타겟 증권사: 한국투자증권 (KIS)**
- 가장 개방적인 API
- 무료 티어 제공
- 좋은 문서화

```java
// 서비스 설계

@Service
public class BrokerageService {

    // 1. 계좌 연동
    public AccountLinkResponse linkAccount(UUID userId, LinkAccountCommand command) {
        // KIS OAuth2 인증
        // 계좌 정보 조회
        // 암호화된 토큰 저장
    }

    // 2. 보유 종목 동기화
    public List<HoldingInfo> syncHoldings(UUID userId, UUID accountId) {
        // 증권사 API에서 보유 종목 조회
        // 우리 Asset 엔티티로 매핑
        // user_holdings 테이블 업데이트
    }

    // 3. 포트폴리오 비교
    public PortfolioComparisonResponse compareWithTarget(
        UUID userId, UUID accountId, UUID portfolioId) {

        // 현재 보유 종목
        List<HoldingInfo> current = getHoldings(userId, accountId);

        // 목표 배분
        List<PortfolioAsset> target = portfolioAssetRepository
            .findByPortfolioId(portfolioId);

        // 차이 계산
        List<RebalancingAction> actions = calculateDiff(current, target);

        return PortfolioComparisonResponse.builder()
            .currentHoldings(current)
            .targetAllocation(target)
            .rebalancingNeeded(actions)
            .estimatedCost(calculateCost(actions))
            .build();
    }
}
```

**데이터베이스 스키마:**
```sql
CREATE TABLE brokerage_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    broker_code VARCHAR(20) NOT NULL, -- 'KIS', 'NH', etc
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(100),
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_brokerage_accounts_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_holdings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    brokerage_account_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    quantity DECIMAL(18,6) NOT NULL,
    avg_price DECIMAL(18,2) NOT NULL,
    current_price DECIMAL(18,2),
    last_synced_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_holdings_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_holdings_account FOREIGN KEY (brokerage_account_id)
        REFERENCES brokerage_accounts(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_holdings_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id)
);

CREATE INDEX idx_user_holdings_user_account ON user_holdings(user_id, brokerage_account_id);
CREATE INDEX idx_user_holdings_asset ON user_holdings(asset_id);
```

**API 엔드포인트:**
```
POST   /accounts/link                           - 증권 계좌 연동
GET    /accounts                                 - 연동된 계좌 목록
DELETE /accounts/{id}                           - 계좌 연동 해제
POST   /accounts/{id}/sync                      - 보유 종목 동기화
GET    /accounts/{id}/holdings                  - 현재 보유 종목 조회
GET    /accounts/{id}/compare/{portfolioId}     - 목표 포트폴리오와 비교
```

**기술 작업:**
1. KIS API 문서 조사
2. OAuth2 플로우 구현
3. 증권사 API 클라이언트 생성 (RestTemplate)
4. 토큰 암호화/복호화 (AES-256)
5. 백그라운드 동기화 스케줄링 (일일)
6. 에러 핸들링 추가 (API 장애, 토큰 만료)
7. 계좌 연동 UI 생성

**사용자 가치:**
- 실제 vs 목표 배분 비교
- 포트폴리오 편차 추적
- 정확히 무엇을 사고팔아야 하는지 파악

---

#### 4개월차: 리밸런싱 가이드
**우선순위: MEDIUM**

```java
// 리밸런싱 알고리즘

public RebalancingPlan createRebalancingPlan(
    UUID userId, UUID accountId, UUID portfolioId) {

    // 1. 현재 보유 종목
    List<HoldingInfo> current = userHoldingRepository
        .findByUserIdAndAccountId(userId, accountId);

    // 2. 목표 배분
    List<PortfolioAsset> target = portfolioAssetRepository
        .findByPortfolioId(portfolioId);

    // 3. 총 계좌 가치 계산
    BigDecimal totalValue = current.stream()
        .map(h -> h.quantity * h.currentPrice)
        .sum();

    // 4. 목표 가치 계산
    Map<UUID, BigDecimal> targetValues = target.stream()
        .collect(Collectors.toMap(
            PortfolioAsset::getAssetId,
            pa -> totalValue.multiply(pa.weightPct).divide(100)
        ));

    // 5. 차이 계산
    List<RebalancingAction> actions = new ArrayList<>();

    for (PortfolioAsset pa : target) {
        BigDecimal targetValue = targetValues.get(pa.getAssetId());
        BigDecimal currentValue = getCurrentValue(current, pa.getAssetId());
        BigDecimal diff = targetValue.subtract(currentValue);

        if (diff.abs().compareTo(threshold) > 0) {
            actions.add(RebalancingAction.builder()
                .assetId(pa.getAssetId())
                .action(diff.signum() > 0 ? "BUY" : "SELL")
                .targetShares(calculateShares(diff, currentPrice))
                .estimatedAmount(diff.abs())
                .build());
        }
    }

    return RebalancingPlan.builder()
        .actions(actions)
        .totalBuyAmount(calculateBuy(actions))
        .totalSellAmount(calculateSell(actions))
        .estimatedFee(calculateFee(actions))
        .estimatedTax(calculateTax(actions))
        .build();
}
```

**API 엔드포인트:**
```
POST /accounts/{id}/rebalancing-plan/{portfolioId} - 리밸런싱 계획 생성
GET  /accounts/{id}/rebalancing-history           - 과거 리밸런싱 이력
```

**기술 작업:**
1. 리밸런싱 알고리즘 구현
2. 수수료/세금 계산 추가 (한국: 거래세 0.015%, 증권거래세 0.23%)
3. 단주 처리 (정수로 반올림)
4. 임계값 설정 추가 (차이가 5% 미만이면 리밸런싱 안 함)
5. 리밸런싱 이력 저장
6. 알림 시스템 생성

**사용자 가치:**
- 명확한 액션 플랜: "AAPL 5주 매수, TSLA 3주 매도"
- 비용 사전 파악 (수수료, 세금)
- 리밸런싱 이력 추적

---

#### 5개월차: 커뮤니티 확장
**우선순위: MEDIUM**

**기능:**
1. **유저 팔로우 시스템**
```sql
CREATE TABLE user_follows (
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT fk_user_follows_follower FOREIGN KEY (follower_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_follows_following FOREIGN KEY (following_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_follows_follower ON user_follows(follower_id);
CREATE INDEX idx_user_follows_following ON user_follows(following_id);
```

2. **피드 알고리즘**
```java
public List<PortfolioShare> getPersonalizedFeed(UUID userId, Pageable pageable) {
    // 1. 팔로우한 사용자의 포트폴리오 (50% 가중치)
    List<PortfolioShare> followingPosts = getFollowingPosts(userId);

    // 2. 인기 포트폴리오 (30% 가중치)
    List<PortfolioShare> popularPosts = getPopularPosts();

    // 3. 유사한 위험 프로필 (20% 가중치)
    List<PortfolioShare> similarPosts = getSimilarRiskPosts(userId);

    // 병합 및 순위 매기기
    return mergeAndRank(followingPosts, popularPosts, similarPosts, pageable);
}
```

3. **포트폴리오 성과 리더보드**
```java
public LeaderboardResponse getLeaderboard(String period, Pageable pageable) {
    // period: "1M", "3M", "1Y", "ALL"

    // 모든 공개 포트폴리오의 수익률 계산
    // 총 수익률로 순위 매기기
    // 최소 팔로워 수로 필터링 (게이밍 방지)

    return LeaderboardResponse.builder()
        .period(period)
        .rankings(rankings)
        .build();
}
```

**API 엔드포인트:**
```
POST /users/{id}/follow              - 사용자 팔로우
DELETE /users/{id}/unfollow          - 언팔로우
GET /users/{id}/followers            - 팔로워 조회
GET /users/{id}/following            - 팔로잉 조회
GET /community/feed                  - 개인화된 피드
GET /community/leaderboard           - 성과 리더보드
GET /community/trending              - 트렌딩 포트폴리오
```

**사용자 가치:**
- 전문 투자자 발견
- 최고 성과자에게서 영감 받기
- 투자 중심 소셜 네트워크 구축

---

**Phase 2 성공 지표:**
- MAU: 500명
- 증권 계좌 연동률: 30%
- 리밸런싱 계획 생성: 연동 사용자의 20%
- 팔로우율: 사용자당 평균 5명
- 커뮤니티 참여: 하루 50개 댓글

---

### Phase 3: 자동화 (3개월)
**목표: 원활한 실행**

#### 6-7개월차: 원클릭 리밸런싱
**우선순위: HIGH**

**접근: 증권사 앱으로 딥링크**
(직접 거래보다 안전 - 투자자문 규제 회피)

```java
public DeepLinkResponse createRebalancingDeepLink(
    UUID userId, UUID accountId, RebalancingPlan plan) {

    // 1. 미리 채워진 주문 데이터 생성
    List<OrderData> orders = plan.getActions().stream()
        .map(action -> OrderData.builder()
            .symbol(getSymbol(action.assetId))
            .side(action.action) // BUY or SELL
            .quantity(action.targetShares)
            .orderType("MARKET")
            .build())
        .collect(Collectors.toList());

    // 2. 딥링크 생성
    String deepLink = brokerageService.createOrderDeepLink(
        accountId, orders);

    // 3. 클릭 추적 (분석용)
    rebalancingHistoryRepository.save(
        RebalancingHistory.builder()
            .userId(userId)
            .accountId(accountId)
            .portfolioId(plan.portfolioId)
            .actions(plan.actions)
            .status("PENDING")
            .build()
    );

    return DeepLinkResponse.builder()
        .deepLink(deepLink)
        .disclaimer("증권사 앱으로 이동하여 주문을 확인합니다")
        .build();
}
```

**플로우:**
1. 사용자가 "리밸런싱 실행" 클릭
2. 앱에서 면책 조항 + 요약 표시
3. 사용자 확인
4. 미리 채워진 주문으로 증권사 앱 열기
5. 사용자가 증권사 앱에서 검토 및 확인
6. 증권사에서 주문 실행

**법적 안전:**
- 우리는 거래를 실행하지 않음
- "정보"만 제공
- 증권사 앱에서 최종 결정

**기술 작업:**
1. KIS 앱의 딥링크 형식 조사
2. 딥링크 생성 구현
3. 리밸런싱 이력 추적 추가
4. 면책 조항이 있는 확인 UI 생성
5. 분석 추가 (클릭률, 완료율)

---

#### 8개월차: 법적 컴플라이언스 & 규제
**우선순위: CRITICAL**

**작업:**
1. **법률 자문**
   - 금융 규제 변호사 고용
   - 투자자문업 라이선스 필요 여부 검토
   - 필요시 규제 신고 준비

2. **면책 조항 & 약관**
   - 모든 화면에 투자 위험 면책 조항 추가
   - 이용약관 업데이트
   - "교육 목적으로만" 메시지 추가
   - "과거 성과 ≠ 미래 결과" 경고 포함

3. **데이터 프라이버시**
   - GDPR 준수 (EU 타겟팅 시)
   - 개인정보보호법 준수
   - 모든 증권사 자격증명 암호화 (AES-256)
   - 사용자 데이터 내보내기/삭제 추가

4. **감사 추적**
   - 모든 사용자 행동 로그 (포트폴리오 생성, 리밸런싱)
   - 동의 이력 저장
   - 면책 조항 수락 기록 보관

**기술 작업:**
1. 면책 조항 모달 추가
2. 동의 추적 구현
3. 암호화 강화
4. 감사 로그 시스템 생성
5. 데이터 내보내기 API 추가

---

#### 9개월차: 고급 기능
**우선순위: LOW**

**1. 예약 리밸런싱**
```java
@Scheduled(cron = "0 0 9 * * MON") // 매주 월요일 오전 9시
public void checkScheduledRebalancing() {
    List<RebalancingSchedule> schedules =
        rebalancingScheduleRepository.findByEnabledTrue();

    for (RebalancingSchedule schedule : schedules) {
        RebalancingPlan plan = createRebalancingPlan(
            schedule.userId, schedule.accountId, schedule.portfolioId);

        if (needsRebalancing(plan)) {
            notificationService.send(schedule.userId,
                "포트폴리오 리밸런싱이 필요합니다!");
        }
    }
}
```

**2. 세금 손실 수확 가이드**
```java
public TaxLossHarvestingResponse findTaxLossOpportunities(
    UUID userId, UUID accountId) {

    List<HoldingInfo> holdings = getHoldings(userId, accountId);

    List<TaxLossOpportunity> opportunities = holdings.stream()
        .filter(h -> h.currentPrice < h.avgPrice) // 손실 포지션
        .filter(h -> h.holdingDays >= 365) // 장기 자본 손실
        .map(h -> TaxLossOpportunity.builder()
            .asset(h.asset)
            .unrealizedLoss(h.unrealizedLoss)
            .taxSavings(h.unrealizedLoss * 0.22) // 22% 세율
            .build())
        .collect(Collectors.toList());

    return TaxLossHarvestingResponse.builder()
        .opportunities(opportunities)
        .totalTaxSavings(calculateTotal(opportunities))
        .build();
}
```

**3. 배당 추적**
```java
public DividendCalendarResponse getDividendCalendar(
    UUID userId, UUID accountId) {

    List<HoldingInfo> holdings = getHoldings(userId, accountId);

    List<DividendEvent> upcomingDividends = holdings.stream()
        .map(h -> dividendService.getUpcomingDividends(h.assetId))
        .flatMap(List::stream)
        .sorted(Comparator.comparing(DividendEvent::exDate))
        .collect(Collectors.toList());

    return DividendCalendarResponse.builder()
        .upcomingDividends(upcomingDividends)
        .estimatedAnnualIncome(calculateAnnualIncome(holdings))
        .build();
}
```

---

**Phase 3 성공 지표:**
- MAU: 1,000명
- 리밸런싱 실행률: 연동 사용자의 10% (월별)
- 예약 리밸런싱: 연동 사용자의 30%
- 법적 컴플라이언스: 규제 이슈 0건
- 사용자 만족도: 4.5점 이상

---

## 💰 수익화 전략

### Phase 1: 무료 (사용자 확보)
**목표: 1,000 MAU**

모든 기능 무료:
- 무제한 포트폴리오
- 무제한 백테스팅
- 전체 커뮤니티 접근

**초점: 제품-시장 적합성**

---

### Phase 2: 프리미엄 모델
**목표: 10% 전환율**

#### 무료 티어:
- 포트폴리오 3개
- 백테스트 월 5회
- 기본 커뮤니티 (보기만)
- 증권 계좌 연동 1개

#### 프리미엄 티어 (월 ₩9,900 또는 연 ₩99,000):
- 무제한 포트폴리오
- 무제한 백테스팅
- 전체 커뮤니티 기능 (댓글, 팔로우)
- 무제한 증권 계좌 연동
- 리밸런싱 알림
- 우선 지원
- 신규 기능 조기 접근
- 광고 없는 경험

**매출 예상 (MAU 1,000):**
- 프리미엄 사용자: 100명 (10% 전환)
- 월 매출: 100 × ₩9,900 = ₩990,000
- 연 매출: ₩11,880,000

---

### Phase 3: 파트너십 매출
**목표: 수익 다각화**

#### 1. 증권사 CPA (신규 고객 획득)
- 신규 계좌 개설: 계좌당 ₩30,000
- 첫 거래 완료: 거래당 ₩10,000

**매출 예상 (MAU 1,000):**
- 계좌 연동률: 30% = 300명
- 신규 계좌율: 50% = 150개 신규 계좌
- CPA 매출: 150 × ₩30,000 = 월 ₩4,500,000

#### 2. 프리미엄 전문가 포트폴리오
- 전문 투자자가 유료로 포트폴리오 공유
- 플랫폼 30% 수수료
- 전문가는 반복 수익 획득

**매출 예상:**
- 전문가 포트폴리오 10개 (각 월 ₩5,000)
- 포트폴리오당 평균 50명 구독
- 매출: 10 × 50 × ₩5,000 × 0.3 = 월 ₩750,000

#### 3. 교육 콘텐츠
- 프리미엄 강좌
- 투자 전략 가이드
- 비디오 튜토리얼

**매출 예상:**
- 강좌 5개 (각 ₩29,000)
- 월 20건 구매
- 매출: 5 × 20 × ₩29,000 = 월 ₩2,900,000

---

### 총 매출 예상

**MAU 1,000:**
- 프리미엄 구독: 월 ₩990,000
- 증권사 CPA: 월 ₩4,500,000
- 전문가 포트폴리오: 월 ₩750,000
- 교육 콘텐츠: 월 ₩2,900,000
- **합계: 월 ₩9,140,000 (연 ₩109M)**

**MAU 10,000:**
- 프리미엄 구독: 월 ₩9,900,000
- 증권사 CPA: 월 ₩45,000,000
- 전문가 포트폴리오: 월 ₩7,500,000
- 교육 콘텐츠: 월 ₩29,000,000
- **합계: 월 ₩91,400,000 (연 ₩1.1B)**

---

## ⚠️ 위험 관리

### 1. 법적 & 규제 위험

**위험:** 투자자문업 라이선스 필요

**영향:** 높음 (서비스 중단)

**완화:**
- 금융 규제 변호사 고용 (8개월차)
- "투자 조언"이 아닌 "교육 도구"로 포지셔닝
- 모든 화면에 면책 조항 추가
- 사용자 행동 감사 추적 보관
- 사용자가 모든 결정을 명시적으로 확인

**비상 계획:**
- 라이선스 필요 시 조기 신청 (6-12개월 프로세스)
- 라이선스 보유 기업과 파트너십 (화이트라벨)
- 순수 "포트폴리오 시뮬레이터"로 전환 (실제 거래 없음)

---

### 2. 기술 위험

**위험:** 증권사 API 다운타임/변경

**영향:** 중간 (서비스 저하)

**완화:**
- 여러 증권사 지원 (한투, NH, 미래에셋)
- 폴백 메커니즘 구현
- 24시간 데이터 캐싱
- API 헬스 지속적 모니터링
- 사용자에게 문제 사전 알림

**비상 계획:**
- 수동 데이터 가져오기 옵션
- 데이터 제공업체와 파트너십 (FnGuide, WISEfn)

---

### 3. 데이터 프라이버시 위험

**위험:** 사용자 금융 데이터 유출

**영향:** 치명적 (평판 + 법적)

**완화:**
- 모든 토큰 암호화 (AES-256)
- 실제 계좌 비밀번호 저장하지 않음
- 정기 보안 감사
- Phase 2 전 침투 테스트
- 버그 바운티 프로그램
- SOC 2 인증 (향후)

**비상 계획:**
- 사이버 보험
- 사고 대응 계획
- 법률 팀 계약

---

### 4. 경쟁 위험

**위험:** 대형 핀테크 시장 진입

**영향:** 높음 (사용자 확보 비용 ↑)

**완화:**
- 네트워크 효과 (커뮤니티)
- 독특한 UX (Arena 드래프팅)
- 교육 콘텐츠 해자
- 빠른 반복 (2주 스프린트)

**차별화:**
- **토스/카카오뱅크**: 뱅킹 중심, 투자 아님
- **미래에셋/삼성**: 상품 판매 중심
- **로보어드바이저**: 알고리즘만, 커뮤니티 없음
- **우리**: 학습 + 커뮤니티 + 쉬운 실행

**비상 계획:**
- B2B로 전환 (은행/증권사 화이트라벨)
- 니치 집중 (예: ESG 포트폴리오만)

---

### 5. 시장 위험

**위험:** 약세장 → 사용자 이탈

**영향:** 중간 (참여 ↓, 매출 ↓)

**완화:**
- 성과보다 학습 강조
- 방어적 포트폴리오 전략 추가
- 콘텐츠: "약세장에서 투자하는 법"
- 성장이 아닌 보존에 초점 전환

**비상 계획:**
- 주식 외 다각화 (채권, 원자재, 암호화폐)
- 시장 중립 전략 추가

---

## 📈 성공 지표 (KPI)

### 제품 지표

#### Phase 1 (1-2개월):
- **DAU**: 100명
- **주간 활성률**: 40%
- **백테스트 사용**: 사용자의 50%
- **포트폴리오 공유율**: 20%
- **평균 세션 시간**: 5분
- **리텐션 (D7)**: 30%

#### Phase 2 (3-5개월):
- **MAU**: 500명
- **증권 계좌 연동률**: 30%
- **리밸런싱 계획 생성**: 연동 사용자의 20%
- **팔로우율**: 사용자당 평균 5명
- **커뮤니티 참여**: 하루 50개 댓글
- **리텐션 (D30)**: 40%

#### Phase 3 (6-9개월):
- **MAU**: 1,000명
- **리밸런싱 실행**: 월 10%
- **프리미엄 전환**: 10%
- **NPS**: 50+
- **리텐션 (D90)**: 50%

---

### 비즈니스 지표

#### Phase 2:
- **MRR (월 반복 수익)**: ₩1M
- **CAC (고객 확보 비용)**: < ₩50,000
- **LTV (생애 가치)**: > ₩150,000
- **LTV/CAC 비율**: > 3x

#### Phase 3:
- **MRR**: ₩9M
- **CAC**: < ₩30,000 (유기적 성장)
- **LTV**: > ₩300,000
- **이탈률**: < 5%/월
- **바이럴 계수**: > 0.5 (사용자 1명이 0.5명의 신규 사용자 유입)

---

## 🚀 다음 단계

### 즉시 실행 (1주차):
1. ✅ 로드맵 문서화 (이 파일)
2. ⬜ 프로젝트 추적 설정 (GitHub Projects 또는 Notion)
3. ⬜ 백테스팅 기술 설계 문서 작성
4. ⬜ 분석 설정 (Google Analytics + Mixpanel)
5. ⬜ 베타 테스터 10명 모집

### Phase 1 착수 (2주차):
1. ⬜ 백테스팅 MVP 구현
2. ⬜ 스테이징 배포
3. ⬜ 10명 사용자로 알파 테스트
4. ⬜ 피드백 수집
5. ⬜ 반복 개선

### 법적 준비 (3개월차):
1. ⬜ 투자자문업 규제 조사
2. ⬜ 금융 변호사와 상담
3. ⬜ 이용약관 초안 작성
4. ⬜ 면책 조항 준비

---

## 📝 미해결 질문

### 기술:
1. 실시간 가격 업데이트? (WebSocket vs 폴링)
2. 모바일 앱 또는 웹 우선?
3. 어떤 클라우드 제공업체? (AWS vs GCP vs Naver Cloud)
4. 캐싱 전략? (Redis vs Memcached)
5. 백그라운드 작업 처리? (현재 Spring Batch vs Quartz)

### 제품:
1. 소셜 기능: 포트폴리오 복사 허용?
2. 게이미피케이션: 배지, 업적?
3. 알림: 푸시 vs 이메일 vs 둘 다?
4. 국제화: 첫날부터 영어 지원?
5. 콘텐츠: 블로그 vs 비디오 vs 둘 다?

### 비즈니스:
1. 무료 티어 한도: 너무 관대하거나 제한적?
2. 가격: 월간 vs 연간 vs 평생?
3. 파트너십: 증권사에 직접 접근?
4. 마케팅: 유료 광고 vs 유기적 vs 인플루언서?
5. 지역 초점: 한국만 또는 미국 시장도?

---

## 🎓 유념할 교훈

1. **작게 시작, 빠르게 반복**: MVP → 피드백 → 개선
2. **사용자 가치 우선**: 매출은 사용량을 따라옴
3. **커뮤니티가 해자**: 네트워크 효과가 경쟁으로부터 보호
4. **컴플라이언스를 조기에**: 불안정한 법적 기반 위에 구축하지 말 것
5. **데이터는 금**: 모든 것을 추적하고, 데이터로 결정
6. **기술 부채는 괜찮음**: 빠르게 배포, 나중에 리팩토링
7. **보안은 치명적**: 한 번의 유출이 모든 것을 망침
8. **집중이 중요**: 기능에 거절, 핵심 가치에 집중

---

**마지막 업데이트:** 2025-01-21
**문서 소유자:** 제품 팀
**검토 주기:** 월간