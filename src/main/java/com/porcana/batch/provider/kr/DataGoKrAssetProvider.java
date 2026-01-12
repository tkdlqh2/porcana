package com.porcana.batch.provider.kr;

import com.porcana.batch.dto.AssetBatchDto;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of Korean asset data provider using data.go.kr API
 * Fetches stock data by querying individual stock codes from CSV files
 */
@Slf4j
@Component
public class DataGoKrAssetProvider implements KrAssetDataProvider {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestTemplate restTemplate;
    private final UniverseTaggingProvider universeTaggingProvider;
    private final String apiKey;
    private final String apiUrl;

    public DataGoKrAssetProvider(
            RestTemplate restTemplate,
            UniverseTaggingProvider universeTaggingProvider,
            @Value("${batch.provider.kr.api-key:}") String apiKey,
            @Value("${batch.provider.kr.api-url:https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService}") String apiUrl
    ) {
        this.restTemplate = restTemplate;
        this.universeTaggingProvider = universeTaggingProvider;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    @Override
    public List<AssetBatchDto> fetchAssets() throws AssetDataProviderException {
        log.info("Fetching Korean market assets from data.go.kr");

        try {
            // Get all stock symbols from CSV files
            Set<String> symbols = getAllStockSymbols();
            log.info("Found {} unique stock symbols from CSV files", symbols.size());

            List<AssetBatchDto> assets = new ArrayList<>();

            // Fetch data for each symbol
            int total = symbols.size();
            int count = 0;
            for (String symbol : symbols) {
                count++;
                try {
                    log.info("Fetching asset {}/{}: {}", count, total, symbol);
                    AssetBatchDto asset = fetchAssetBySymbol(symbol);
                    if (asset != null) {
                        assets.add(asset);
                        log.info("Successfully fetched: {} - {}", symbol, asset.getName());
                    } else {
                        log.warn("No data returned for symbol: {}", symbol);
                    }

                    // Add small delay to avoid rate limiting
                    Thread.sleep(100);

                } catch (Exception e) {
                    log.warn("Failed to fetch data for symbol: {}. Skipping. Error: {}", symbol, e.getMessage());
                    // Continue with next symbol instead of failing entire batch
                }

                // Log progress every 10 symbols
                if (count % 10 == 0) {
                    log.info("Progress: {}/{} symbols processed, {} assets collected", count, total, assets.size());
                }
            }

            log.info("Successfully fetched {} assets from data.go.kr", assets.size());
            return assets;

        } catch (Exception e) {
            log.error("Failed to fetch assets from data.go.kr", e);
            throw new AssetDataProviderException("Failed to fetch Korean market assets from data.go.kr", e);
        }
    }

    /**
     * Get all unique stock symbols from KOSPI200 and KOSDAQ150 CSV files
     */
    private Set<String> getAllStockSymbols() {
        Set<String> symbols = new HashSet<>();
        symbols.addAll(universeTaggingProvider.readKospi200Constituents());
        symbols.addAll(universeTaggingProvider.readKosdaq150Constituents());
        return symbols;
    }

    /**
     * Fetch asset data for a single symbol from data.go.kr API
     */
    private AssetBatchDto fetchAssetBySymbol(String symbol) {
        String url = UriComponentsBuilder.fromHttpUrl(apiUrl + "/getStockPriceInfo")
                .queryParam("serviceKey", apiKey)
                .queryParam("likeSrtnCd", symbol)
                .queryParam("resultType", "json")
                .toUriString();

        try {
            DataGoKrResponse response = restTemplate.getForObject(url, DataGoKrResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("No response for symbol: {}", symbol);
                return null;
            }

            DataGoKrResponse.Header header = response.getResponse().getHeader();
            if (!"00".equals(header.getResultCode())) {
                log.warn("API error for symbol {}: {} - {}", symbol, header.getResultCode(), header.getResultMsg());
                return null;
            }

            DataGoKrResponse.Body body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems().getItem() == null || body.getItems().getItem().isEmpty()) {
                log.warn("No data found for symbol: {}", symbol);
                return null;
            }

            // likeSrtnCd로 조회하면 보통 1개의 결과만 반환됨, 첫 번째 아이템 사용
            return convertToDto(body.getItems().getItem().get(0));

        } catch (Exception e) {
            log.error("Failed to fetch data for symbol: {}", symbol, e);
            return null;
        }
    }

    /**
     * Fetch daily price data for a single asset (latest trading day)
     * Used for daily price updates
     *
     * @param asset The asset to fetch price for
     * @return AssetPrice entity (not yet persisted), or null if no data
     */
    public AssetPrice fetchDailyPrice(Asset asset) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("data.go.kr API key not configured. Skipping daily price fetch.");
            return null;
        }

        // Fetch last 5 days to ensure we get the latest trading day
        LocalDate fiveDaysAgo = LocalDate.now().minusDays(5);
        String beginBasDt = fiveDaysAgo.format(DATE_FORMATTER);
        String endBasDt = LocalDate.now().format(DATE_FORMATTER);

        String url = UriComponentsBuilder.fromHttpUrl(apiUrl + "/getStockPriceInfo")
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
                log.warn("No response for symbol: {}", asset.getSymbol());
                return null;
            }

            DataGoKrResponse.Header header = response.getResponse().getHeader();
            if (!"00".equals(header.getResultCode())) {
                log.warn("API error for symbol {}: {} - {}",
                        asset.getSymbol(), header.getResultCode(), header.getResultMsg());
                return null;
            }

            DataGoKrResponse.Body body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems().getItem() == null
                    || body.getItems().getItem().isEmpty()) {
                log.warn("No data found for symbol: {}", asset.getSymbol());
                return null;
            }

            // Get the most recent price (first item in the list)
            DataGoKrResponse.Item latestItem = body.getItems().getItem().get(0);

            LocalDate priceDate = parseDate(latestItem.getBasDt());
            BigDecimal price = parsePrice(latestItem.getClpr());
            Long volume = latestItem.getTrqu();

            if (price == null || volume == null) {
                log.warn("Invalid price data for symbol: {}", asset.getSymbol());
                return null;
            }

            return AssetPrice.builder()
                    .asset(asset)
                    .priceDate(priceDate)
                    .price(price)
                    .volume(volume)
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch daily price for symbol: {}", asset.getSymbol(), e);
            return null;
        }
    }

    /**
     * Fetch historical price data for a single asset
     * Fetches data from 1 year ago to now
     *
     * @param asset The asset to fetch prices for
     * @return List of AssetPrice entities (not yet persisted)
     */
    public List<AssetPrice> fetchHistoricalPrices(Asset asset) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("data.go.kr API key not configured. Skipping historical price fetch.");
            return new ArrayList<>();
        }

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        String beginBasDt = oneYearAgo.format(DATE_FORMATTER);
        String endBasDt = LocalDate.now().format(DATE_FORMATTER);

        String url = UriComponentsBuilder.fromHttpUrl(apiUrl + "/getStockPriceInfo")
                .queryParam("serviceKey", apiKey)
                .queryParam("likeSrtnCd", asset.getSymbol())
                .queryParam("beginBasDt", beginBasDt)
                .queryParam("endBasDt", endBasDt)
                .queryParam("resultType", "json")
                .queryParam("numOfRows", "500")
                .toUriString();

        try {
            log.info("Fetching historical prices for {}: from {} to {}",
                    asset.getSymbol(), beginBasDt, endBasDt);

            DataGoKrResponse response = restTemplate.getForObject(url, DataGoKrResponse.class);

            if (response == null || response.getResponse() == null) {
                log.warn("No response for symbol: {}", asset.getSymbol());
                return new ArrayList<>();
            }

            DataGoKrResponse.Header header = response.getResponse().getHeader();
            if (!"00".equals(header.getResultCode())) {
                log.warn("API error for symbol {}: {} - {}",
                        asset.getSymbol(), header.getResultCode(), header.getResultMsg());
                return new ArrayList<>();
            }

            DataGoKrResponse.Body body = response.getResponse().getBody();
            if (body == null || body.getItems() == null || body.getItems().getItem() == null) {
                log.warn("No data found for symbol: {}", asset.getSymbol());
                return new ArrayList<>();
            }

            List<AssetPrice> assetPrices = new ArrayList<>();
            for (DataGoKrResponse.Item item : body.getItems().getItem()) {
                try {
                    LocalDate priceDate = parseDate(item.getBasDt());
                    BigDecimal price = parsePrice(item.getClpr());
                    Long volume = item.getTrqu();

                    if (price != null && volume != null) {
                        AssetPrice assetPrice = AssetPrice.builder()
                                .asset(asset)
                                .priceDate(priceDate)
                                .price(price)
                                .volume(volume)
                                .build();
                        assetPrices.add(assetPrice);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse price data for {}: {}", asset.getSymbol(), e.getMessage());
                }
            }

            log.info("Fetched {} historical price records for {}", assetPrices.size(), asset.getSymbol());
            return assetPrices;

        } catch (Exception e) {
            log.error("Failed to fetch historical prices for symbol: {}", asset.getSymbol(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public String getProviderName() {
        return "DATA_GO_KR";
    }

    /**
     * Convert data.go.kr API response item to AssetBatchDto
     */
    private AssetBatchDto convertToDto(DataGoKrResponse.Item item) {
        // Determine asset type
        Asset.AssetType type = determineAssetType(item.getItmsNm());

        // Parse base date
        LocalDate asOf = parseDate(item.getBasDt());

        return AssetBatchDto.builder()
                .market(Asset.Market.KR)
                .symbol(item.getSrtnCd())
                .name(item.getItmsNm())
                .type(type)
                .sector(null) // Korean stock sector data to be added later
                .active(false) // Will be set to true by universe tagging step
                .asOf(asOf)
                .build();
    }

    /**
     * Determine asset type (STOCK or ETF)
     * Korean ETFs typically have specific naming patterns or ISIN codes
     */
    private Asset.AssetType determineAssetType(String name) {
        if (name == null) {
            return Asset.AssetType.STOCK;
        }

        // Common ETF indicators in Korean market
        String nameLower = name.toLowerCase();
        if (nameLower.contains("etf") ||
            nameLower.contains("etn") ||
            nameLower.contains("레버리지") ||
            nameLower.contains("인버스")) {
            return Asset.AssetType.ETF;
        }

        return Asset.AssetType.STOCK;
    }

    /**
     * Parse date string from data.go.kr format (yyyyMMdd)
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }

        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}. Using current date.", dateStr);
            return LocalDate.now();
        }
    }

    /**
     * Parse price string to BigDecimal
     * Korean stock prices are typically in KRW (whole numbers)
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