package com.porcana.domain.asset.entity.personality;

import lombok.Getter;

/**
 * 포트폴리오 내 자산의 역할
 */
@Getter
public enum Role {
    CORE("핵심", "포트폴리오의 안정적 기반이 되는 종목"),
    GROWTH("성장", "높은 성장 가능성을 추구하는 종목"),
    DEFENSIVE("방어", "시장 하락기에 안정성을 제공하는 종목"),
    INCOME("인컴", "배당/이자를 통한 현금흐름 창출 종목"),
    SATELLITE("위성", "특정 테마/섹터에 집중 투자하는 종목"),
    HEDGE("헤지", "포트폴리오 위험 분산을 위한 대체자산");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
