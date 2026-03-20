package com.porcana.domain.asset.entity;

import lombok.Getter;

/**
 * 배당 주기
 */
@Getter
public enum DividendFrequency {
    NONE("무배당", "배당 없음"),
    MONTHLY("월배당", "매월 배당 지급"),
    QUARTERLY("분기배당", "분기별 배당 지급"),
    SEMI_ANNUAL("반기배당", "반기별 배당 지급"),
    ANNUAL("연배당", "연간 1회 배당 지급"),
    IRREGULAR("불규칙", "불규칙한 배당 지급"),
    UNKNOWN("알 수 없음", "배당 주기 정보 없음");

    private final String displayName;
    private final String description;

    DividendFrequency(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}