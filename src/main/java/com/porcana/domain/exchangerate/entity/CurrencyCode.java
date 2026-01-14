package com.porcana.domain.exchangerate.entity;

/**
 * 통화 코드 ENUM
 * 한국수출입은행 API에서 제공하는 주요 통화
 */
public enum CurrencyCode {
    // 주요 통화
    USD("미국 달러"),
    JPY("일본 엔"),
    EUR("유럽연합 유로"),
    CNY("중국 위안"),

    // 아시아/오세아니아
    HKD("홍콩 달러"),
    TWD("대만 달러"),
    GBP("영국 파운드"),
    OMR("오만 리얄"),
    CAD("캐나다 달러"),
    CHF("스위스 프랑"),
    SEK("스웨덴 크로나"),
    AUD("호주 달러"),
    NZD("뉴질랜드 달러"),
    CZK("체코 코루나"),
    CLP("칠레 페소"),
    TRY("터키 리라"),
    MNT("몽골 투그릭"),
    ILS("이스라엘 세켈"),
    DKK("덴마크 크로네"),
    NOK("노르웨이 크로네"),
    SAR("사우디 리얄"),
    KWD("쿠웨이트 디나르"),
    BHD("바레인 디나르"),
    AED("아랍에미리트 디르함"),
    JOD("요르단 디나르"),
    EGP("이집트 파운드"),
    THB("태국 바트"),
    SGD("싱가포르 달러"),
    MYR("말레이시아 링기트"),
    IDR("인도네시아 루피아"),
    QAR("카타르 리얄"),
    KZT("카자흐스탄 텐게"),
    BND("브루나이 달러"),
    INR("인도 루피"),
    PKR("파키스탄 루피"),
    BDT("방글라데시 타카"),
    PHP("필리핀 페소"),
    MXN("멕시코 페소"),
    BRL("브라질 헤알"),
    VND("베트남 동"),
    ZAR("남아프리카 공화국 랜드"),
    RUB("러시아 루블"),
    HUF("헝가리 포린트"),
    PLN("폴란드 즈워티"),
    FJD("피지 달러");

    private final String description;

    CurrencyCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 통화 코드 문자열로부터 CurrencyCode enum 반환
     * 매칭되지 않으면 null 반환
     */
    public static CurrencyCode fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        try {
            return CurrencyCode.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
