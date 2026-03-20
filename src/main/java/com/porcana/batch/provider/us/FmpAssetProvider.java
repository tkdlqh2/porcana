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

import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendDataStatus;
import com.porcana.domain.asset.entity.DividendFrequency;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final String HISTORICAL_PRICE_ENDPOINT = "/stable/historical-price-eod/full";
    private static final String HISTORICAL_DIVIDENDS_ENDPOINT = "/api/v3/historical-price-full/stock_dividend";
    private static final String SP500_CSV = "batch/s&p500.csv";
    private static final String NASDAQ100_CSV = "batch/nasdaq100.csv";

    // 배당 수익률 임계값 (소수 기준)
    private static final BigDecimal HIGH_DIVIDEND_THRESHOLD = new BigDecimal("0.04");  // 4% 이상 = 고배당

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
                    .imageUrl(profile.getImage())
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
                boolean isFirstLine = true;
                while ((line = reader.readLine()) != null) {
                    // Skip header line if exists
                    if (isFirstLine && line.toLowerCase().contains("symbol")) {
                        isFirstLine = false;
                        continue;
                    }
                    isFirstLine = false;

                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    // Handle CSV format - take first column
                    String symbol = line.split(",")[0].trim();
                    if (!symbol.isEmpty()) {
                        symbols.add(symbol);
                    }
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

            // Use OHLC if available, otherwise fallback to price field
            Double openVal = latestPrice.getOpen();
            Double highVal = latestPrice.getHigh();
            Double lowVal = latestPrice.getLow();
            Double closeVal = latestPrice.getClose();
            if (closeVal == null) {
                log.warn("No close price for symbol: {}", asset.getSymbol());
                return null;
            }
            if (openVal == null) openVal = closeVal;
            if (highVal == null) highVal = closeVal;
            if (lowVal == null) lowVal = closeVal;

            return AssetPrice.builder()
                    .asset(asset)
                    .priceDate(LocalDate.parse(latestPrice.getDate()))
                    .openPrice(BigDecimal.valueOf(openVal))
                    .highPrice(BigDecimal.valueOf(highVal))
                    .lowPrice(BigDecimal.valueOf(lowVal))
                    .closePrice(BigDecimal.valueOf(closeVal))
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
                // Use OHLC if available, otherwise fallback to price field
                Double openVal = price.getOpen();
                Double highVal = price.getHigh();
                Double lowVal = price.getLow();
                Double closeVal = price.getClose();

                AssetPrice assetPrice = AssetPrice.builder()
                        .asset(asset)
                        .priceDate(LocalDate.parse(price.getDate()))
                        .openPrice(BigDecimal.valueOf(openVal))
                        .highPrice(BigDecimal.valueOf(highVal))
                        .lowPrice(BigDecimal.valueOf(lowVal))
                        .closePrice(BigDecimal.valueOf(closeVal))
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

    /**
     * Fetch image URL for a single symbol from FMP API
     * Used for updating image URLs of existing assets
     *
     * @param symbol The stock symbol
     * @return Image URL, or null if not found
     */
    public String fetchImageUrl(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FMP API key not configured. Skipping image fetch.");
            return null;
        }

        String url = String.format("%s%s?symbol=%s&apikey=%s", baseUrl, PROFILE_ENDPOINT, symbol, apiKey);

        try {
            FmpProfile[] profiles = restTemplate.getForObject(url, FmpProfile[].class);

            if (profiles == null || profiles.length == 0) {
                log.debug("No profile data for symbol: {}", symbol);
                return null;
            }

            FmpProfile profile = profiles[0];
            return profile.getImage();

        } catch (Exception e) {
            log.warn("Failed to fetch image for symbol: {}. Error: {}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public String getProviderName() {
        return "FMP";
    }

    /**
     * Fetch dividend data for a single symbol from FMP API
     *
     * @param symbol The stock symbol
     * @return DividendData containing yield, frequency, category, etc.
     */
    public DividendData fetchDividendData(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FMP API key not configured. Skipping dividend fetch.");
            return null;
        }

        try {
            // 1. Fetch profile for lastDiv and price
            FmpProfile profile = fetchProfile(symbol);
            if (profile == null) {
                log.debug("No profile data for symbol: {}", symbol);
                return null;
            }

            // 2. Fetch historical dividends for frequency analysis
            List<FmpDividend> dividends = fetchHistoricalDividends(symbol);

            // 3. Calculate dividend data
            return calculateDividendData(symbol, profile, dividends);

        } catch (Exception e) {
            log.warn("Failed to fetch dividend data for symbol: {}. Error: {}", symbol, e.getMessage());
            return null;
        }
    }

    private FmpProfile fetchProfile(String symbol) {
        String url = String.format("%s%s?symbol=%s&apikey=%s", baseUrl, PROFILE_ENDPOINT, symbol, apiKey);

        try {
            FmpProfile[] profiles = restTemplate.getForObject(url, FmpProfile[].class);
            if (profiles == null || profiles.length == 0) {
                return null;
            }
            return profiles[0];
        } catch (Exception e) {
            log.debug("Failed to fetch profile for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private List<FmpDividend> fetchHistoricalDividends(String symbol) {
        // FMP Historical Dividends API
        String url = String.format("%s/%s/%s?apikey=%s", baseUrl, HISTORICAL_DIVIDENDS_ENDPOINT, symbol, apiKey);

        try {
            FmpDividendResponse response = restTemplate.getForObject(url, FmpDividendResponse.class);
            if (response == null || response.getHistorical() == null) {
                return new ArrayList<>();
            }
            return response.getHistorical();
        } catch (Exception e) {
            log.debug("Failed to fetch historical dividends for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    private DividendData calculateDividendData(String symbol, FmpProfile profile, List<FmpDividend> dividends) {
        // Check if dividend available
        boolean hasDividend = profile.getLastDiv() != null && profile.getLastDiv() > 0;

        if (!hasDividend && dividends.isEmpty()) {
            return DividendData.builder()
                    .dividendAvailable(false)
                    .dividendYield(null)
                    .dividendFrequency(DividendFrequency.NONE)
                    .dividendCategory(DividendCategory.NONE)
                    .dividendDataStatus(DividendDataStatus.VERIFIED)
                    .lastDividendDate(null)
                    .build();
        }

        // Calculate dividend yield from lastDiv and price
        BigDecimal dividendYield = null;
        if (profile.getLastDiv() != null && profile.getPrice() != null && profile.getPrice() > 0) {
            // lastDiv is per-share dividend, need to annualize based on frequency
            // For now, assume lastDiv is already annualized (FMP typically provides TTM dividend)
            double yield = profile.getLastDiv() / profile.getPrice();
            dividendYield = BigDecimal.valueOf(yield).setScale(6, RoundingMode.HALF_UP);
        }

        // Determine frequency from historical dividends
        DividendFrequency frequency = determineDividendFrequency(dividends);

        // Determine category based on yield
        DividendCategory category = determineDividendCategory(dividendYield, profile);

        // Get last dividend date
        LocalDate lastDividendDate = null;
        if (!dividends.isEmpty()) {
            dividends.sort(Comparator.comparing(FmpDividend::getPaymentDate, Comparator.nullsLast(Comparator.reverseOrder())));
            FmpDividend lastDividend = dividends.get(0);
            if (lastDividend.getPaymentDate() != null) {
                try {
                    lastDividendDate = LocalDate.parse(lastDividend.getPaymentDate());
                } catch (Exception e) {
                    log.debug("Failed to parse dividend date for {}: {}", symbol, lastDividend.getPaymentDate());
                }
            }
        }

        return DividendData.builder()
                .dividendAvailable(true)
                .dividendYield(dividendYield)
                .dividendFrequency(frequency)
                .dividendCategory(category)
                .dividendDataStatus(DividendDataStatus.VERIFIED)
                .lastDividendDate(lastDividendDate)
                .build();
    }

    private DividendFrequency determineDividendFrequency(List<FmpDividend> dividends) {
        if (dividends.isEmpty()) {
            return DividendFrequency.UNKNOWN;
        }

        // Filter last 2 years of dividends
        LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
        List<FmpDividend> recentDividends = dividends.stream()
                .filter(d -> {
                    if (d.getPaymentDate() == null) return false;
                    try {
                        LocalDate date = LocalDate.parse(d.getPaymentDate());
                        return date.isAfter(twoYearsAgo);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .sorted(Comparator.comparing(d -> LocalDate.parse(d.getPaymentDate())))
                .toList();

        if (recentDividends.size() < 2) {
            return DividendFrequency.UNKNOWN;
        }

        // Calculate average days between dividends
        long totalDays = 0;
        int intervals = 0;
        for (int i = 1; i < recentDividends.size(); i++) {
            LocalDate prevDate = LocalDate.parse(recentDividends.get(i - 1).getPaymentDate());
            LocalDate currDate = LocalDate.parse(recentDividends.get(i).getPaymentDate());
            totalDays += ChronoUnit.DAYS.between(prevDate, currDate);
            intervals++;
        }

        if (intervals == 0) {
            return DividendFrequency.UNKNOWN;
        }

        long avgDays = totalDays / intervals;

        // Determine frequency based on average interval
        if (avgDays <= 45) {          // ~30 days = monthly
            return DividendFrequency.MONTHLY;
        } else if (avgDays <= 120) {  // ~90 days = quarterly
            return DividendFrequency.QUARTERLY;
        } else if (avgDays <= 210) {  // ~180 days = semi-annual
            return DividendFrequency.SEMI_ANNUAL;
        } else if (avgDays <= 400) {  // ~365 days = annual
            return DividendFrequency.ANNUAL;
        } else {
            return DividendFrequency.IRREGULAR;
        }
    }

    private DividendCategory determineDividendCategory(BigDecimal dividendYield, FmpProfile profile) {
        if (dividendYield == null || dividendYield.compareTo(BigDecimal.ZERO) <= 0) {
            return DividendCategory.NONE;
        }

        // Check for REIT (Real Estate Investment Trust)
        if (profile.getSector() != null &&
                (profile.getSector().toLowerCase().contains("real estate") ||
                 profile.getIndustry() != null && profile.getIndustry().toLowerCase().contains("reit"))) {
            return DividendCategory.REIT_LIKE;
        }

        // Categorize by yield
        if (dividendYield.compareTo(HIGH_DIVIDEND_THRESHOLD) >= 0) {
            return DividendCategory.HIGH_DIVIDEND;
        } else if (dividendYield.compareTo(new BigDecimal("0.02")) >= 0) {
            return DividendCategory.DIVIDEND_GROWTH;
        } else {
            return DividendCategory.HAS_DIVIDEND;
        }
    }

    /**
     * Dividend data result
     */
    @Data
    @lombok.Builder
    public static class DividendData {
        private Boolean dividendAvailable;
        private BigDecimal dividendYield;
        private DividendFrequency dividendFrequency;
        private DividendCategory dividendCategory;
        private DividendDataStatus dividendDataStatus;
        private LocalDate lastDividendDate;
    }

    /**
     * FMP Historical Dividends Response
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpDividendResponse {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("historical")
        private List<FmpDividend> historical;
    }

    /**
     * FMP Dividend Entry
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpDividend {
        @JsonProperty("date")
        private String date;

        @JsonProperty("label")
        private String label;

        @JsonProperty("adjDividend")
        private Double adjDividend;

        @JsonProperty("dividend")
        private Double dividend;

        @JsonProperty("recordDate")
        private String recordDate;

        @JsonProperty("paymentDate")
        private String paymentDate;

        @JsonProperty("declarationDate")
        private String declarationDate;
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

        @JsonProperty("open")
        private Double open;

        @JsonProperty("high")
        private Double high;

        @JsonProperty("low")
        private Double low;

        @JsonProperty("close")
        private Double close;

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