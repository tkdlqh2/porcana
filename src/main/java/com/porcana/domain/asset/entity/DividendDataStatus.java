package com.porcana.domain.asset.entity;

import lombok.Getter;

/**
 * 배당 데이터 수집 상태
 */
@Getter
public enum DividendDataStatus {
    NONE("데이터 없음", "배당 데이터가 수집되지 않음"),
    PARTIAL("일부 수집", "일부 배당 데이터만 수집됨"),
    VERIFIED("검증 완료", "신뢰 가능한 소스에서 수집 완료");

    private final String displayName;
    private final String description;

    DividendDataStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}