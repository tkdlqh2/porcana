package com.porcana.domain.asset.entity;

/**
 * GICS (Global Industry Classification Standard) Sector
 * 글로벌 산업 분류 표준 섹터
 */
public enum Sector {
    MATERIALS("Materials", "Basic Materials", "소재"),
    COMMUNICATION_SERVICES("Communication Services", "Communication Services", "커뮤니케이션 서비스"),
    CONSUMER_DISCRETIONARY("Consumer Discretionary", "Consumer Cyclical", "경기소비재"),
    CONSUMER_STAPLES("Consumer Staples", "Consumer Defensive", "필수소비재"),
    ENERGY("Energy", "Energy", "에너지"),
    FINANCIALS("Financials", "Financial Services", "금융"),
    HEALTH_CARE("Health Care", "Healthcare", "헬스케어"),
    INDUSTRIALS("Industrials", "Industrials", "산업재"),
    REAL_ESTATE("Real Estate", "Real Estate", "부동산"),
    INFORMATION_TECHNOLOGY("Information Technology", "Technology", "정보기술"),
    UTILITIES("Utilities", "Utilities", "유틸리티");

    private final String description;
    private final String fmpName;
    private final String koreanName;

    Sector(String description, String fmpName, String koreanName) {
        this.description = description;
        this.fmpName = fmpName;
        this.koreanName = koreanName;
    }

    public String getDescription() {
        return description;
    }

    public String getFmpName() {
        return fmpName;
    }

    public String getKoreanName() {
        return koreanName;
    }

    /**
     * FMP API에서 반환하는 sector 이름으로 Sector enum 찾기
     */
    public static Sector fromFmpName(String fmpName) {
        if (fmpName == null || fmpName.isBlank()) {
            return null;
        }

        for (Sector sector : values()) {
            if (sector.fmpName.equalsIgnoreCase(fmpName.trim())) {
                return sector;
            }
        }

        return null;
    }
}