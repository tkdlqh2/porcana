package com.porcana.domain.asset.entity;

/**
 * GICS (Global Industry Classification Standard) Sector
 * 글로벌 산업 분류 표준 섹터
 */
public enum Sector {
    MATERIALS("Materials", "Basic Materials"),
    COMMUNICATION_SERVICES("Communication Services", "Communication Services"),
    CONSUMER_DISCRETIONARY("Consumer Discretionary", "Consumer Cyclical"),
    CONSUMER_STAPLES("Consumer Staples", "Consumer Defensive"),
    ENERGY("Energy", "Energy"),
    FINANCIALS("Financials", "Financial Services"),
    HEALTH_CARE("Health Care", "Healthcare"),
    INDUSTRIALS("Industrials", "Industrials"),
    REAL_ESTATE("Real Estate", "Real Estate"),
    INFORMATION_TECHNOLOGY("Information Technology", "Technology"),
    UTILITIES("Utilities", "Utilities");

    private final String description;
    private final String fmpName;

    Sector(String description, String fmpName) {
        this.description = description;
        this.fmpName = fmpName;
    }

    public String getDescription() {
        return description;
    }

    public String getFmpName() {
        return fmpName;
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