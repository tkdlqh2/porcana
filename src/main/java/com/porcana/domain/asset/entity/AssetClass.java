package com.porcana.domain.asset.entity;

/**
 * ETF 자산 클래스 분류
 * ETF의 투자 대상 및 전략을 나타냄
 */
public enum AssetClass {
    /**
     * 주식 인덱스 ETF
     * 예: SPY (S&P 500), KODEX 200
     */
    EQUITY_INDEX("Equity Index"),

    /**
     * 섹터별 ETF
     * 예: XLK (Technology), KODEX 반도체
     */
    SECTOR("Sector"),

    /**
     * 배당 ETF
     * 예: SCHD (US Dividend), TIGER 고배당
     */
    DIVIDEND("Dividend"),

    /**
     * 채권 ETF
     * 예: TLT (20+ Year Treasury), KODEX 국고채10년
     */
    BOND("Bond"),

    /**
     * 원자재 ETF
     * 예: GLD (Gold), KRX 금현물
     */
    COMMODITY("Commodity");

    private final String description;

    AssetClass(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
