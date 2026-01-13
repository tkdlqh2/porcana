package com.porcana.batch.provider.us;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.UniverseTag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of US asset data provider using Financial Modeling Prep (FMP) API
 * Fetches S&P 500 and NASDAQ 100 constituent data by querying individual symbols from CSV files
 */
@Slf4j
@Component
public class FmpAssetProvider implements UsAssetDataProvider {

    private static final String PROFILE_ENDPOINT = "/stable/profile";
    private static final String HISTORICAL_PRICE_ENDPOINT = "/stable/historical-price-eod/light";
    private static final String SP500_CSV = "batch/s&p500.csv";
    private static final String NASDAQ100_CSV = "batch/nasdaq100.csv";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public FmpAssetProvider(
            RestTemplate restTemplate,
            @Value("${batch.provider.us.api-key:}") String apiKey,
            @Value("${batch.provider.us.base-url:https://financialmodelingprep.com}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<AssetBatchDto> fetchAssets() throws AssetDataProviderException {
        log.info("Fetching US market assets from FMP");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FMP API key not configured. Skipping US market fetch.");
            return new ArrayList<>();
        }

        try {
            // Read symbol lists from CSV files
            Set<String> sp500Symbols = readSymbolsFromCsv(SP500_CSV);
            Set<String> nasdaq100Symbols = readSymbolsFromCsv(NASDAQ100_CSV);

            log.info("Found {} S&P 500 symbols and {} NASDAQ 100 symbols",
                    sp500Symbols.size(), nasdaq100Symbols.size());

            // Get all unique symbols
            Set<String> allSymbols = new HashSet<>();
            allSymbols.addAll(sp500Symbols);
            allSymbols.addAll(nasdaq100Symbols);

            log.info("Total unique symbols: {}", allSymbols.size());

            List<AssetBatchDto> assets = new ArrayList<>();
            LocalDate asOf = LocalDate.now();

            int total = allSymbols.size();
            int count = 0;

            for (String symbol : allSymbols) {
                count++;
                try {
                    log.info("Fetching asset {}/{}: {}", count, total, symbol);

                    AssetBatchDto asset = fetchAssetBySymbol(symbol, sp500Symbols, nasdaq100Symbols, asOf);
                    if (asset != null) {
                        assets.add(asset);
                        log.info("Successfully fetched: {} - {}", symbol, asset.getName());
                    } else {
                        log.warn("No data returned for symbol: {}", symbol);
                    }

                    // Add small delay to avoid rate limiting
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Asset fetch interrupted at symbol: {}", symbol);
                    break;
                } catch (Exception e) {
                    log.warn("Failed to fetch data for symbol: {}. Skipping. Error: {}", symbol, e.getMessage());
                }

                // Log progress every 10 symbols
                if (count % 10 == 0) {
                    log.info("Progress: {}/{} symbols processed, {} assets collected", count, total, assets.size());
                }
            }

            log.info("Successfully fetched {} US assets from FMP", assets.size());
            return assets;

        } catch (Exception e) {
            log.error("Failed to fetch US market assets from FMP", e);
            throw new AssetDataProviderException("Failed to fetch US market assets from FMP", e);
        }
    }

    /**
     * Fetch asset data for a single symbol from FMP API
     */
    private AssetBatchDto fetchAssetBySymbol(String symbol, Set<String> sp500Symbols,
                                             Set<String> nasdaq100Symbols, LocalDate asOf) {
        String url = String.format("%s%s?symbol=%s&apikey=%s", baseUrl, PROFILE_ENDPOINT, symbol, apiKey);

        try {
            FmpProfile[] profiles = restTemplate.getForObject(url, FmpProfile[].class);

            if (profiles == null || profiles.length == 0) {
                log.debug("No profile data for symbol: {} (may not exist in FMP database)", symbol);
                return null;
            }

            FmpProfile profile = profiles[0];

            // Determine universe tags based on which CSV files contain this symbol
            List<UniverseTag> universeTags = new ArrayList<>();
            if (sp500Symbols.contains(symbol)) {
                universeTags.add(UniverseTag.SP500);
            }
            if (nasdaq100Symbols.contains(symbol)) {
                universeTags.add(UniverseTag.NASDAQ100);
            }

            // Convert FMP sector name to GICS Sector enum
            Sector sector = Sector.fromFmpName(profile.getSector());
            if (sector == null && profile.getSector() != null) {
                log.warn("Unknown sector from FMP for {}: {}. Setting sector to null.",
                        symbol, profile.getSector());
            }

            return AssetBatchDto.builder()
                    .market(Asset.Market.US)
                    .symbol(symbol)
                    .name(profile.getCompanyName() != null ? profile.getCompanyName() : symbol)
                    .type(profile.isEtf() ? Asset.AssetType.ETF : Asset.AssetType.STOCK)
                    .sector(sector)
                    .universeTags(universeTags)
                    .active(true) // All fetched assets are active
                    .asOf(asOf)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to fetch profile for symbol: {}. Error: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Read symbol list from CSV file
     */
    private Set<String> readSymbolsFromCsv(String csvPath) {
        Set<String> symbols = new HashSet<>();

        try {
            ClassPathResource resource = new ClassPathResource(csvPath);
            if (!resource.exists()) {
                log.warn("CSV file not found: {}. Skipping.", csvPath);
                return symbols;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    symbols.add(line);
                }
            }

            log.info("Read {} symbols from {}", symbols.size(), csvPath);

        } catch (IOException e) {
            log.error("Failed to read CSV file: {}", csvPath, e);
        }

        return symbols;
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
            log.warn("FMP API key not configured. Skipping daily price fetch.");
            return null;
        }

        // Fetch last 3 days to ensure we get the latest trading day
        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        String fromDate = threeDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);

        String url = String.format("%s%s?from=%s&symbol=%s&apikey=%s",
                baseUrl, HISTORICAL_PRICE_ENDPOINT, fromDate, asset.getSymbol(), apiKey);

        try {
            FmpHistoricalPrice[] prices = restTemplate.getForObject(url, FmpHistoricalPrice[].class);

            if (prices == null || prices.length == 0) {
                log.warn("No daily price data for symbol: {}", asset.getSymbol());
                return null;
            }

            // Get the most recent price (first in array, as FMP returns newest first)
            FmpHistoricalPrice latestPrice = prices[0];

            return AssetPrice.builder()
                    .asset(asset)
                    .priceDate(LocalDate.parse(latestPrice.getDate()))
                    .price(BigDecimal.valueOf(latestPrice.getPrice()))
                    .volume(latestPrice.getVolume())
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
            log.warn("FMP API key not configured. Skipping historical price fetch.");
            return new ArrayList<>();
        }

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        String fromDate = oneYearAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);

        String url = String.format("%s%s?from=%s&symbol=%s&apikey=%s",
                baseUrl, HISTORICAL_PRICE_ENDPOINT, fromDate, asset.getSymbol(), apiKey);

        try {
            log.info("Fetching historical prices for {}: from {} to now", asset.getSymbol(), fromDate);

            FmpHistoricalPrice[] prices = restTemplate.getForObject(url, FmpHistoricalPrice[].class);

            if (prices == null || prices.length == 0) {
                log.warn("No historical price data for symbol: {}", asset.getSymbol());
                return new ArrayList<>();
            }

            List<AssetPrice> assetPrices = new ArrayList<>();
            for (FmpHistoricalPrice price : prices) {
                AssetPrice assetPrice = AssetPrice.builder()
                        .asset(asset)
                        .priceDate(LocalDate.parse(price.getDate()))
                        .price(BigDecimal.valueOf(price.getPrice()))
                        .volume(price.getVolume())
                        .build();
                assetPrices.add(assetPrice);
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
        return "FMP";
    }

    /**
     * FMP Historical Price Response
     * https://site.financialmodelingprep.com/developer/docs#historical-price-ceod-light
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpHistoricalPrice {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("date")
        private String date;

        @JsonProperty("price")
        private Double price;

        @JsonProperty("volume")
        private Long volume;
    }

    /**
     * FMP Company Profile Response
     * https://site.financialmodelingprep.com/developer/docs#company-profile
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpProfile {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("price")
        private Double price;

        @JsonProperty("beta")
        private Double beta;

        @JsonProperty("volAvg")
        private Long volAvg;

        @JsonProperty("mktCap")
        private Long mktCap;

        @JsonProperty("lastDiv")
        private Double lastDiv;

        @JsonProperty("range")
        private String range;

        @JsonProperty("changes")
        private Double changes;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("cik")
        private String cik;

        @JsonProperty("isin")
        private String isin;

        @JsonProperty("cusip")
        private String cusip;

        @JsonProperty("exchange")
        private String exchange;

        @JsonProperty("exchangeShortName")
        private String exchangeShortName;

        @JsonProperty("industry")
        private String industry;

        @JsonProperty("website")
        private String website;

        @JsonProperty("description")
        private String description;

        @JsonProperty("ceo")
        private String ceo;

        @JsonProperty("sector")
        private String sector;

        @JsonProperty("country")
        private String country;

        @JsonProperty("fullTimeEmployees")
        private String fullTimeEmployees;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        private String address;

        @JsonProperty("city")
        private String city;

        @JsonProperty("state")
        private String state;

        @JsonProperty("zip")
        private String zip;

        @JsonProperty("dcfDiff")
        private Double dcfDiff;

        @JsonProperty("dcf")
        private Double dcf;

        @JsonProperty("image")
        private String image;

        @JsonProperty("ipoDate")
        private String ipoDate;

        @JsonProperty("defaultImage")
        private Boolean defaultImage;

        @JsonProperty("isEtf")
        private Boolean isEtf;

        @JsonProperty("isActivelyTrading")
        private Boolean isActivelyTrading;

        @JsonProperty("isAdr")
        private Boolean isAdr;

        @JsonProperty("isFund")
        private Boolean isFund;

        public boolean isEtf() {
            return Boolean.TRUE.equals(isEtf) || Boolean.TRUE.equals(isFund);
        }
    }
}