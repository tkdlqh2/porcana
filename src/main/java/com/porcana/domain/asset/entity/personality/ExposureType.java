package com.porcana.domain.asset.entity.personality;

import lombok.Getter;

/**
 * 투자 노출 유형
 */
@Getter
public enum ExposureType {
    BROAD_INDEX("광범위 인덱스", "시장 전체를 추종"),
    SINGLE_STOCK("개별 주식", "단일 기업에 투자"),
    SECTOR("섹터", "특정 산업/섹터에 집중"),
    DIVIDEND("배당", "배당 수익 중심"),
    BOND("채권", "채권/고정수익"),
    COMMODITY("원자재", "원자재/실물자산");

    private final String displayName;
    private final String description;

    ExposureType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
