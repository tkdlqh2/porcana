package com.porcana.domain.asset.entity;

/**
 * 종목 유니버스 태그
 * 카드 풀 구성 및 필터링에 사용
 */
public enum UniverseTag {
    // US Market
    SP500("S&P 500"),
    NASDAQ100("NASDAQ 100"),
    DOW30("Dow Jones 30"),

    // KR Market
    KOSPI200("KOSPI 200"),
    KOSDAQ150("KOSDAQ 150"),

    // ETF Categories
    ETF_CORE("Core ETF"),
    ETF_SECTOR("Sector ETF"),
    ETF_THEMATIC("Thematic ETF"),

    // Additional Categories (for future expansion)
    MEGA_CAP("Mega Cap"),
    LARGE_CAP("Large Cap"),
    MID_CAP("Mid Cap"),
    GROWTH("Growth"),
    VALUE("Value");

    private final String description;

    UniverseTag(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}