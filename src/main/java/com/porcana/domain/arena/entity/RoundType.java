package com.porcana.domain.arena.entity;

/**
 * 라운드 타입
 * - PRE_ROUND: 라운드 0 (투자성향 + 섹터 동시 선택)
 * - ASSET: 라운드 1-10 (종목 선택)
 */
public enum RoundType {
    PRE_ROUND,     // 투자성향 + 섹터 선택 라운드 (Round 0)
    ASSET          // 종목 선택 라운드 (Round 1-10)
}
