package com.porcana.domain.asset.entity.personality;

import lombok.Getter;

/**
 * UX용 자산 성격 요약
 */
@Getter
public enum Persona {
    STABLE("안정형", "꾸준하고 예측 가능한 성과를 추구"),
    BALANCED("균형형", "위험과 수익의 균형을 추구"),
    GROWTH("성장형", "중장기 자본 성장을 추구"),
    AGGRESSIVE("공격형", "고위험 고수익을 추구"),
    DEFENSIVE("방어형", "자본 보존을 최우선"),
    CASHFLOW("현금흐름형", "정기적인 배당/이자 수익 추구"),
    THEMATIC("테마형", "특정 트렌드/테마에 집중");

    private final String displayName;
    private final String description;

    Persona(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
