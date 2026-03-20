package com.porcana.domain.portfolio.entity.deck;

import lombok.Getter;

/**
 * 포트폴리오 덱 스타일
 */
@Getter
public enum DeckStyle {
    AGGRESSIVE("공격형", "고위험 고수익을 추구하는 포트폴리오"),
    BALANCED("균형형", "위험과 수익의 균형을 추구하는 포트폴리오"),
    DEFENSIVE("방어형", "안정적인 자산 배분 중심 포트폴리오"),
    CASHFLOW("현금흐름형", "배당/이자 수익 중심 포트폴리오"),
    GROWTH("성장형", "성장주 중심 포트폴리오"),
    THEMATIC("테마형", "특정 섹터/테마 집중 포트폴리오");

    private final String displayName;
    private final String description;

    DeckStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
