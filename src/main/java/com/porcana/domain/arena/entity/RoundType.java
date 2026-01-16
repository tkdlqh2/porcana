package com.porcana.domain.arena.entity;

/**
 * 라운드 타입
 * - RISK_PROFILE: 라운드 1 (투자성향 선택)
 * - SECTOR: 라운드 2 (섹터 선택)
 * - ASSET: 라운드 3-12 (종목 선택)
 */
public enum RoundType {
    RISK_PROFILE,  // 투자성향 선택 라운드
    SECTOR,        // 섹터 선택 라운드
    ASSET          // 종목 선택 라운드
}
