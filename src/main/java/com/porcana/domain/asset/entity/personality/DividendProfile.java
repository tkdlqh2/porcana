package com.porcana.domain.asset.entity.personality;

import lombok.Getter;

/**
 * 배당 특성 프로필
 */
@Getter
public enum DividendProfile {
    NONE("무배당", "배당이 없거나 매우 적음"),
    HAS_DIVIDEND("배당 있음", "배당을 지급하지만 주 목적은 아님"),
    DIVIDEND_FOCUSED("배당 중심", "높은 배당 수익률 추구"),
    INCOME_CORE("인컴 핵심", "배당이 투자의 핵심 목적");

    private final String displayName;
    private final String description;

    DividendProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
