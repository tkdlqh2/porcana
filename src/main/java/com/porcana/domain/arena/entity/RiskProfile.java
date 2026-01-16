package com.porcana.domain.arena.entity;

/**
 * 투자 성향 (Risk Profile)
 * 사용자의 투자 성향을 3단계로 분류
 */
public enum RiskProfile {
    AGGRESSIVE("공격적", "고위험 고수익을 추구하는 투자 성향"),
    BALANCED("균형", "위험과 수익의 균형을 추구하는 투자 성향"),
    CONSERVATIVE("보수적", "안정적인 수익을 추구하는 저위험 투자 성향");

    private final String displayName;
    private final String description;

    RiskProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
