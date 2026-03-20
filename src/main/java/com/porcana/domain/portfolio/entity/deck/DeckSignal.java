package com.porcana.domain.portfolio.entity.deck;

import lombok.Getter;

/**
 * 포트폴리오 구성 시그널 (주의 사항)
 */
@Getter
public enum DeckSignal {
    SECTOR_CONCENTRATION("섹터 집중", "특정 섹터 비중이 40% 이상입니다"),
    HIGH_RISK_EXPOSURE("고위험 노출", "고위험 종목 비중이 60% 이상입니다"),
    LOW_DEFENSE("방어력 부족", "방어적 종목 비중이 10% 이하입니다"),
    HIGH_CONCENTRATION("종목 집중", "상위 3종목 비중이 55% 이상입니다"),
    HIGH_INCOME("인컴 중심", "인컴형 종목 비중이 40% 이상입니다"),
    MARKET_IMBALANCE("시장 불균형", "특정 시장(KR/US) 비중이 80% 이상입니다"),
    MARKET_CONCENTRATION("시장 집중", "특정 시장(KR/US) 비중이 90% 이상입니다"),
    LOW_DIVERSIFICATION("분산 부족", "종목 수가 5개 미만입니다"),
    DIVIDEND_TILT("배당 성향", "배당 중심 종목 비중이 25% 이상입니다");

    private final String displayName;
    private final String description;

    DeckSignal(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
