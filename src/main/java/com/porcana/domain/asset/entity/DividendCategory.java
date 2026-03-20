package com.porcana.domain.asset.entity;

import lombok.Getter;

/**
 * 배당 성향 원천 태그
 */
@Getter
public enum DividendCategory {
    NONE("무배당", "배당 없음"),
    HAS_DIVIDEND("배당 있음", "배당을 지급하지만 핵심은 아님"),
    DIVIDEND_GROWTH("배당성장", "배당 성장을 추구하는 종목"),
    HIGH_DIVIDEND("고배당", "높은 배당수익률을 제공"),
    REIT_LIKE("리츠형", "리츠 또는 부동산 관련 인컴"),
    COVERED_CALL_LIKE("커버드콜형", "옵션 프리미엄 기반 분배"),
    UNKNOWN("알 수 없음", "배당 성향 정보 없음");

    private final String displayName;
    private final String description;

    DividendCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}