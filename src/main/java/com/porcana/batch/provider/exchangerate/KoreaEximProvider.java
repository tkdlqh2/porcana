package com.porcana.batch.provider.exchangerate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provider for fetching exchange rates from Korea Exim Bank API
 * API: https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON
 */
@Slf4j
@Component
public class KoreaEximProvider {

    private static final String API_URL = "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestTemplate restTemplate;
    private final String apiKey;

    public KoreaEximProvider(
            RestTemplate restTemplate,
            @Value("${batch.provider.exchangerate.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Fetch exchange rates for a specific date
     *
     * @param date Target date for exchange rates
     * @return List of ExchangeRate entities
     */
    public List<ExchangeRate> fetchExchangeRates(LocalDate date) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Korea Exim Bank API key not configured. Skipping exchange rate fetch.");
            return new ArrayList<>();
        }

        String searchDate = date.format(DATE_FORMATTER);
        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("authkey", apiKey)
                .queryParam("searchdate", searchDate)
                .queryParam("data", "AP01")
                .toUriString();

        try {
            log.info("Fetching exchange rates for date: {}", searchDate);

            KoreaEximResponse[] responses = restTemplate.getForObject(url, KoreaEximResponse[].class);

            if (responses == null || responses.length == 0) {
                log.warn("No exchange rate data returned for date: {}", searchDate);
                return new ArrayList<>();
            }

            List<ExchangeRate> exchangeRates = new ArrayList<>();
            for (KoreaEximResponse response : responses) {
                try {
                    // Skip invalid or error responses
                    if (response.getResult() != null && response.getResult() == 2) {
                        log.warn("API returned error for currency: {}", response.getCurUnit());
                        continue;
                    }

                    // Parse currency code and multiplier (e.g., "USD" -> ("USD", 1), "JPY(100)" -> ("JPY", 100))
                    CurrencyParseResult parseResult = parseCurrencyUnit(response.getCurUnit());
                    if (parseResult == null) {
                        continue;
                    }

                    // Convert to CurrencyCode enum
                    CurrencyCode currencyCode = CurrencyCode.fromCode(parseResult.code());
                    if (currencyCode == null) {
                        log.debug("Unsupported currency code: {}. Skipping.", parseResult.code());
                        continue;
                    }

                    // Parse rates
                    BigDecimal baseRate = parseRate(response.getDealBasR());
                    BigDecimal buyRate = parseRate(response.getTtb());
                    BigDecimal sellRate = parseRate(response.getTts());

                    if (baseRate == null) {
                        log.warn("Invalid base rate for currency: {}", currencyCode);
                        continue;
                    }

                    // Apply multiplier if present (e.g., JPY(100) means rate is for 100 units)
                    // Divide by multiplier to get rate per single unit
                    int multiplier = parseResult.multiplier();
                    if (multiplier > 1) {
                        baseRate = baseRate.divide(BigDecimal.valueOf(multiplier), 4, java.math.RoundingMode.HALF_UP);
                        if (buyRate != null) {
                            buyRate = buyRate.divide(BigDecimal.valueOf(multiplier), 4, java.math.RoundingMode.HALF_UP);
                        }
                        if (sellRate != null) {
                            sellRate = sellRate.divide(BigDecimal.valueOf(multiplier), 4, java.math.RoundingMode.HALF_UP);
                        }
                    }

                    ExchangeRate exchangeRate = ExchangeRate.builder()
                            .currencyCode(currencyCode)
                            .currencyName(response.getCurNm())
                            .baseRate(baseRate)
                            .buyRate(buyRate)
                            .sellRate(sellRate)
                            .exchangeDate(date)
                            .build();

                    exchangeRates.add(exchangeRate);

                } catch (Exception e) {
                    log.warn("Failed to parse exchange rate: {}", response.getCurUnit(), e);
                }
            }

            log.info("Fetched {} exchange rates for date: {}", exchangeRates.size(), searchDate);
            return exchangeRates;

        } catch (Exception e) {
            log.error("Failed to fetch exchange rates from Korea Exim Bank API", e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse currency code and multiplier from cur_unit field
     * Examples: "USD" -> ("USD", 1), "JPY(100)" -> ("JPY", 100)
     */
    private CurrencyParseResult parseCurrencyUnit(String curUnit) {
        if (curUnit == null || curUnit.trim().isEmpty()) {
            return null;
        }

        int multiplier = 1;
        String code = curUnit.trim();

        // Extract multiplier from parentheses (e.g., "JPY(100)" -> multiplier=100)
        Pattern pattern = Pattern.compile("\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(curUnit);
        if (matcher.find()) {
            multiplier = Integer.parseInt(matcher.group(1));
            code = curUnit.replaceAll("\\(.*\\)", "").trim();
        }

        // Some currencies might have spaces
        code = code.split("\\s+")[0];

        return new CurrencyParseResult(code, multiplier);
    }

    /**
     * Parse rate string to BigDecimal
     * Removes commas and converts to BigDecimal
     */
    private BigDecimal parseRate(String rateStr) {
        if (rateStr == null || rateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // Remove commas from the rate string
            String cleanRate = rateStr.replace(",", "");
            return new BigDecimal(cleanRate);
        } catch (Exception e) {
            log.warn("Failed to parse rate: {}", rateStr);
            return null;
        }
    }

    /**
     * Korea Exim Bank API Response
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class KoreaEximResponse {
        @JsonProperty("result")
        private Integer result;  // 1: 성공, 2: 실패

        @JsonProperty("cur_unit")
        private String curUnit;  // 통화 코드 (USD, JPY(100), EUR 등)

        @JsonProperty("cur_nm")
        private String curNm;  // 국가/통화명

        @JsonProperty("ttb")
        private String ttb;  // 송금 받을 때 (살 때)

        @JsonProperty("tts")
        private String tts;  // 송금 보낼 때 (팔 때)

        @JsonProperty("deal_bas_r")
        private String dealBasR;  // 매매기준율

        @JsonProperty("bkpr")
        private String bkpr;  // 장부가격

        @JsonProperty("yy_efee_r")
        private String yyEfeeR;  // 연환가료율

        @JsonProperty("ten_dd_efee_r")
        private String tenDdEfeeR;  // 10일환가료율

        @JsonProperty("kftc_deal_bas_r")
        private String kftcDealBasR;  // 서울외국환중개 매매기준율

        @JsonProperty("kftc_bkpr")
        private String kftcBkpr;  // 서울외국환중개 장부가격
    }

    /**
     * Result of parsing currency unit string
     *
     * @param code       Currency code (e.g., "USD", "JPY")
     * @param multiplier Multiplier for the rate (e.g., 1 for USD, 100 for JPY)
     */
    private record CurrencyParseResult(String code, int multiplier) {
    }
}
