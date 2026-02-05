package com.porcana.batch.provider.kr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response wrapper for data.go.kr API
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataGoKrResponse {

    @JsonProperty("response")
    private Response response;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        @JsonProperty("header")
        private Header header;

        @JsonProperty("body")
        private Body body;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        @JsonProperty("resultCode")
        private String resultCode;

        @JsonProperty("resultMsg")
        private String resultMsg;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("items")
        private Items items;

        @JsonProperty("numOfRows")
        private String numOfRows;

        @JsonProperty("pageNo")
        private String pageNo;

        @JsonProperty("totalCount")
        private String totalCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        @JsonProperty("item")
        private List<Item> item;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("basDt")
        private String basDt;  // 기준일

        @JsonProperty("srtnCd")
        private String srtnCd;  // 종목코드

        @JsonProperty("isinCd")
        private String isinCd;  // ISIN 코드

        @JsonProperty("itmsNm")
        private String itmsNm;  // 종목명

        @JsonProperty("mrktCtg")
        private String mrktCtg;  // 시장구분 (KOSPI, KOSDAQ, KONEX)

        @JsonProperty("clpr")
        private String clpr;  // 종가

        @JsonProperty("vs")
        private Integer vs;  // 전일대비

        @JsonProperty("fltRt")
        private Double fltRt;  // 등락률

        @JsonProperty("mkp")
        private String mkp;  // 시가

        @JsonProperty("hipr")
        private String hipr;  // 고가

        @JsonProperty("lopr")
        private String lopr;  // 저가

        @JsonProperty("trqu")
        private Long trqu;  // 거래량

        @JsonProperty("trPrc")
        private Long trPrc;  // 거래대금

        @JsonProperty("lstgStCnt")
        private Long lstgStCnt;  // 상장주식수

        @JsonProperty("mrktTotAmt")
        private Long mrktTotAmt;  // 시가총액
    }
}