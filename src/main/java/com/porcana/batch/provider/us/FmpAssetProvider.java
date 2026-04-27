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
    private static final String HISTORICAL_PRICE_ENDPOINT = "/stable/historical-price-eod/full";
    private static final String DIVIDENDS_ENDPOINT = "/stable/dividends";
    private static final String RATIOS_TTM_ENDPOINT = "/stable/ratios-ttm";
    private static final String SP500_CSV = "batch/s&p500.csv";
    private static final String NASDAQ100_CSV = "batch/nasdaq100.csv";
    private static final String AMEX_CSV = "batch/amex.csv";
    private static final String DOW30_CSV = "batch/dowjones.csv";

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
            Set<String> amexSymbols = readSymbolsFromCsv(AMEX_CSV);
            Set<String> dow30Symbols = readSymbolsFromCsv(DOW30_CSV);

            log.info("Found {} S&P 500 symbols, {} NASDAQ 100 symbols, {} AMEX symbols, {} Dow Jones 30 symbols",
                    sp500Symbols.size(), nasdaq100Symbols.size(), amexSymbols.size(), dow30Symbols.size());

            // Get all unique symbols
            Set<String> allSymbols = new HashSet<>();
            allSymbols.addAll(sp500Symbols);
            allSymbols.addAll(nasdaq100Symbols);
            allSymbols.addAll(amexSymbols);
            allSymbols.addAll(dow30Symbols);

            log.info("Total unique symbols: {}", allSymbols.size());

            List<AssetBatchDto> assets = new ArrayList<>();
            LocalDate asOf = LocalDate.now();

            int total = allSymbols.size();
            int count = 0;

            for (String symbol : allSymbols) {
                count++;
                try {
                    log.info("Fetching asset {}/{}: {}", count, total, symbol);

                    AssetBatchDto asset = fetchAssetBySymbol(symbol, sp500Symbols, nasdaq100Symbols, amexSymbols, dow30Symbols, asOf);
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
                                             Set<String> nasdaq100Symbols, Set<String> amexSymbols,
                                             Set<String> dow30Symbols, LocalDate asOf) {
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
            if (amexSymbols.contains(symbol)) {
                universeTags.add(UniverseTag.AMEX);
            }
            if (dow30Symbols.contains(symbol)) {
                universeTags.add(UniverseTag.DOW30);
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
                    .active(Boolean.TRUE.equals(profile.getIsActivelyTrading()))
                    .asOf(asOf)
                    .imageUrl(profile.getImage())
                    .description(profile.getDescription())
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

    /**
     * Lightweight profile fetch used by the weekly asset status check job.
     * Returns only the fields needed to update an existing asset's status and metadata.
     * Returns null if the symbol is not found or the API is unavailable.
     */
    public ProfileUpdateData fetchProfileUpdateData(String symbol) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FMP API key not configured. Skipping profile fetch for {}", symbol);
            return null;
        }

        FmpProfile profile = fetchProfile(symbol);
        if (profile == null) return null;

        return new ProfileUpdateData(
                Boolean.TRUE.equals(profile.getIsActivelyTrading()),
                profile.getImage(),
                profile.getDescription()
        );
    }

    /**
     * Result of a lightweight profile fetch for the status check job.
     */
    public record ProfileUpdateData(boolean activelyTrading, String imageUrl, String description) {}

    @Override
    public String getProviderName() {
        return "FMP";
    }

    /**
     * Fetch dividend data for a single symbol from FMP API (stable endpoints)
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
            // 1. Fetch dividend history from /stable/dividends
            List<FmpDividend> dividends = fetchDividends(symbol);

            // 2. Fetch ratios for TTM yield
            FmpRatiosTtm ratios = fetchRatiosTtm(symbol);

            // 3. Fetch profile for sector/industry info
            FmpProfile profile = fetchProfile(symbol);

            // 4. Calculate dividend data
            return calculateDividendData(symbol, dividends, ratios, profile);

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

    private List<FmpDividend> fetchDividends(String symbol) {
        // FMP Stable Dividends API: /stable/dividends?symbol=AAPL
        String url = String.format("%s%s?symbol=%s&apikey=%s", baseUrl, DIVIDENDS_ENDPOINT, symbol, apiKey);

        try {
            FmpDividend[] dividends = restTemplate.getForObject(url, FmpDividend[].class);
            if (dividends == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(List.of(dividends));
        } catch (Exception e) {
            log.debug("Failed to fetch dividends for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    private FmpRatiosTtm fetchRatiosTtm(String symbol) {
        // FMP Stable Ratios TTM API: /stable/ratios-ttm?symbol=AAPL
        String url = String.format("%s%s?symbol=%s&apikey=%s", baseUrl, RATIOS_TTM_ENDPOINT, symbol, apiKey);

        try {
            FmpRatiosTtm[] ratios = restTemplate.getForObject(url, FmpRatiosTtm[].class);
            if (ratios == null || ratios.length == 0) {
                return null;
            }
            return ratios[0];
        } catch (Exception e) {
            log.debug("Failed to fetch ratios-ttm for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private DividendData calculateDividendData(String symbol, List<FmpDividend> dividends,
                                                FmpRatiosTtm ratios, FmpProfile profile) {
        // Check if dividend available
        boolean hasDividend = !dividends.isEmpty() ||
                (ratios != null && ratios.getDividendYieldTTM() != null && ratios.getDividendYieldTTM() > 0);

        if (!hasDividend) {
            return DividendData.builder()
                    .dividendAvailable(false)
                    .dividendYield(null)
                    .dividendFrequency(DividendFrequency.NONE)
                    .dividendCategory(DividendCategory.NONE)
                    .dividendDataStatus(DividendDataStatus.VERIFIED)
                    .lastDividendDate(null)
                    .build();
        }

        // Get dividend yield from ratios-ttm (most accurate)
        BigDecimal dividendYield = null;
        if (ratios != null && ratios.getDividendYieldTTM() != null) {
            dividendYield = BigDecimal.valueOf(ratios.getDividendYieldTTM()).setScale(6, RoundingMode.HALF_UP);
        }

        // Determine frequency from dividend data (FMP provides it directly)
        DividendFrequency frequency = determineDividendFrequency(dividends);

        // Determine category based on yield
        DividendCategory category = determineDividendCategory(dividendYield, profile);

        // Get last dividend date
        LocalDate lastDividendDate = null;
        if (!dividends.isEmpty()) {
            // Dividends are already sorted by date desc from API
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

        // FMP provides frequency directly in dividend data
        FmpDividend firstDividend = dividends.get(0);
        if (firstDividend.getFrequency() != null) {
            return switch (firstDividend.getFrequency().toLowerCase()) {
                case "monthly" -> DividendFrequency.MONTHLY;
                case "quarterly" -> DividendFrequency.QUARTERLY;
                case "semi-annual", "semi-annually" -> DividendFrequency.SEMI_ANNUAL;
                case "annual", "annually" -> DividendFrequency.ANNUAL;
                default -> DividendFrequency.IRREGULAR;
            };
        }

        return DividendFrequency.UNKNOWN;
    }

    private DividendCategory determineDividendCategory(BigDecimal dividendYield, FmpProfile profile) {
        if (dividendYield == null || dividendYield.compareTo(BigDecimal.ZERO) <= 0) {
            return DividendCategory.NONE;
        }

        // Check for REIT (Real Estate Investment Trust)
        if (profile != null && profile.getSector() != null &&
                (profile.getSector().toLowerCase().contains("real estate") ||
                 (profile.getIndustry() != null && profile.getIndustry().toLowerCase().contains("reit")))) {
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
     * FMP Dividend Entry (from /stable/dividends)
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpDividend {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("date")
        private String date;

        @JsonProperty("recordDate")
        private String recordDate;

        @JsonProperty("paymentDate")
        private String paymentDate;

        @JsonProperty("declarationDate")
        private String declarationDate;

        @JsonProperty("adjDividend")
        private Double adjDividend;

        @JsonProperty("dividend")
        private Double dividend;

        @JsonProperty("yield")
        private Double yield;

        @JsonProperty("frequency")
        private String frequency;
    }

    /**
     * FMP Ratios TTM (from /stable/ratios-ttm)
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpRatiosTtm {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("dividendYieldTTM")
        private Double dividendYieldTTM;

        @JsonProperty("dividendPerShareTTM")
        private Double dividendPerShareTTM;

        @JsonProperty("dividendPayoutRatioTTM")
        private Double dividendPayoutRatioTTM;
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
