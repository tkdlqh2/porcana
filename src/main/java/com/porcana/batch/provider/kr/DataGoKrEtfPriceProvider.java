package com.porcana.batch.provider.kr;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
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

/**
 * Provider for fetching Korean ETF prices from data.go.kr API
 * Uses GetSecuritiesProductInfoService/getETFPriceInfo endpoint
 */
@Slf4j
@Component
public class DataGoKrEtfPriceProvider {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ETF_PRICE_API_URL = "https://apis.data.go.kr/1160100/service/GetSecuritiesProductInfoService";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public DataGoKrEtfPriceProvider(
            RestTemplate restTemplate,
            @Value("${batch.provider.kr.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Fetch daily price data for a single ETF asset (latest trading day)
     *
     * @param asset The ETF asset to fetch price for
     * @return AssetPrice entity (not yet persisted), or null if no data
     */
    public AssetPrice fetchDailyPrice(Asset asset) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("data.go.kr API key not configured. Skipping ETF daily price fetch.");
            return null;
        }

        // Fetch last 5 days to ensure we get the latest trading day
        LocalDate fiveDaysAgo = LocalDate.now().minusDays(5);
        String beginBasDt = fiveDaysAgo.format(DATE_FORMATTER);
        String endBasDt = LocalDate.now().format(DATE_FORMATTER);

        String url = UriComponentsBuilder.fromHttpUrl(ETF_PRICE_API_URL + "/getETFPriceInfo")
                .queryParam("serviceKey", apiKey)
                .queryParam("likeSrtnCd", asset.getSymbol())
                .queryParam("beginBasDt", beginBasDt)
                .queryParam("endBasDt", endBasDt)
                .queryParam("resultType", "json")
                .queryParam("numOfRows", "10")
                .toUriString();

        try {
            DataGoKrResponse response = restTemplate.getForObject(url, DataGoKrResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("No response for ETF symbol: {}", asset.getSymbol());
                return null;
            }

            DataGoKrResponse.Header header = response.getResponse().getHeader();
            if (!"00".equals(header.getResultCode())) {
                log.warn("API error for ETF symbol {}: {} - {}",
                        asset.getSymbol(), header.getResultCode(), header.getResultMsg());
                return null;
            }

            DataGoKrResponse.Body body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems().getItem() == null
                    || body.getItems().getItem().isEmpty()) {
                log.warn("No data found for ETF symbol: {}", asset.getSymbol());
                return null;
            }

            // Get the most recent price (first item in the list)
            DataGoKrResponse.Item latestItem = body.getItems().getItem().get(0);

            LocalDate priceDate = parseDate(latestItem.getBasDt());
            BigDecimal price = parsePrice(latestItem.getClpr());
            Long volume = latestItem.getTrqu();

            if (priceDate == null || price == null || volume == null) {
                log.warn("Invalid price data for ETF symbol: {} (date: {}, price: {}, volume: {})",
                        asset.getSymbol(), priceDate, price, volume);
                return null;
            }

            return AssetPrice.builder()
                    .asset(asset)
                    .priceDate(priceDate)
                    .price(price)
                    .volume(volume)
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch daily price for ETF symbol: {}", asset.getSymbol(), e);
            return null;
        }
    }

    /**
     * Fetch historical price data for a single ETF asset
     * Fetches data from 1 year ago to now
     *
     * @param asset The ETF asset to fetch prices for
     * @return List of AssetPrice entities (not yet persisted)
     */
    public List<AssetPrice> fetchHistoricalPrices(Asset asset) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("data.go.kr API key not configured. Skipping ETF historical price fetch.");
            return new ArrayList<>();
        }

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        String beginBasDt = oneYearAgo.format(DATE_FORMATTER);
        String endBasDt = LocalDate.now().format(DATE_FORMATTER);

        String url = UriComponentsBuilder.fromHttpUrl(ETF_PRICE_API_URL + "/getETFPriceInfo")
                .queryParam("serviceKey", apiKey)
                .queryParam("likeSrtnCd", asset.getSymbol())
                .queryParam("beginBasDt", beginBasDt)
                .queryParam("endBasDt", endBasDt)
                .queryParam("resultType", "json")
                .queryParam("numOfRows", "500")
                .toUriString();

        try {
            log.info("Fetching historical prices for ETF {}: from {} to {}",
                    asset.getSymbol(), beginBasDt, endBasDt);

            DataGoKrResponse response = restTemplate.getForObject(url, DataGoKrResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("No response for ETF symbol: {}", asset.getSymbol());
                return new ArrayList<>();
            }

            DataGoKrResponse.Header header = response.getResponse().getHeader();
            if (!"00".equals(header.getResultCode())) {
                log.warn("API error for ETF symbol {}: {} - {}",
                        asset.getSymbol(), header.getResultCode(), header.getResultMsg());
                return new ArrayList<>();
            }

            DataGoKrResponse.Body body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems().getItem() == null) {
                log.warn("No data found for ETF symbol: {}", asset.getSymbol());
                return new ArrayList<>();
            }

            List<AssetPrice> assetPrices = new ArrayList<>();
            for (DataGoKrResponse.Item item : body.getItems().getItem()) {
                try {
                    LocalDate priceDate = parseDate(item.getBasDt());
                    BigDecimal price = parsePrice(item.getClpr());
                    Long volume = item.getTrqu();

                    if (priceDate != null && price != null && volume != null) {
                        AssetPrice assetPrice = AssetPrice.builder()
                                .asset(asset)
                                .priceDate(priceDate)
                                .price(price)
                                .volume(volume)
                                .build();
                        assetPrices.add(assetPrice);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse price data for ETF {}: {}", asset.getSymbol(), e.getMessage());
                }
            }

            log.info("Fetched {} historical price records for ETF {}", assetPrices.size(), asset.getSymbol());
            return assetPrices;

        } catch (Exception e) {
            log.error("Failed to fetch historical prices for ETF symbol: {}", asset.getSymbol(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Parse date string from data.go.kr format (yyyyMMdd)
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    /**
     * Parse price string to BigDecimal
     * Korean ETF prices are typically in KRW (whole numbers)
     */
    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(priceStr.trim());
        } catch (Exception e) {
            log.warn("Failed to parse price: {}", priceStr);
            return null;
        }
    }
}
