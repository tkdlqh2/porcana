package com.porcana.batch.provider.kr;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.domain.asset.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
            for (String symbol : symbols) {
                try {
                    AssetBatchDto asset = fetchAssetBySymbol(symbol);
                    if (asset != null) {
                        assets.add(asset);
                    }

                    // Add small delay to avoid rate limiting
                    Thread.sleep(100);

                } catch (Exception e) {
                    log.warn("Failed to fetch data for symbol: {}. Skipping. Error: {}", symbol, e.getMessage());
                    // Continue with next symbol instead of failing entire batch
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
            if (body == null || body.getItems() == null || body.getItems().getItem() == null) {
                log.warn("No data found for symbol: {}", symbol);
                return null;
            }

            return convertToDto(body.getItems().getItem());

        } catch (Exception e) {
            log.error("Failed to fetch data for symbol: {}", symbol, e);
            return null;
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
        // Determine exchange based on market category
        String exchange = determineExchange(item.getMrktCtg());

        // Determine asset type based on exchange
        Asset.AssetType type = determineAssetType(exchange, item.getItmsNm());

        // Parse base date
        LocalDate asOf = parseDate(item.getBasDt());

        return AssetBatchDto.builder()
                .market(Asset.Market.KR)
                .symbol(item.getSrtnCd())
                .exchange(exchange)
                .name(item.getItmsNm())
                .type(type)
                .active(false) // Will be set to true by universe tagging step
                .asOf(asOf)
                .build();
    }

    /**
     * Determine exchange from market category
     * mrktCtg values: "KOSPI", "KOSDAQ", "KONEX", etc.
     */
    private String determineExchange(String mrktCtg) {
        if (mrktCtg == null) {
            return "UNKNOWN";
        }

        String normalized = mrktCtg.toUpperCase().trim();

        if (normalized.contains("KOSPI")) {
            return "KOSPI";
        } else if (normalized.contains("KOSDAQ")) {
            return "KOSDAQ";
        } else if (normalized.contains("KONEX")) {
            return "KONEX";
        }

        return normalized;
    }

    /**
     * Determine asset type (STOCK or ETF)
     * Korean ETFs typically have specific naming patterns or ISIN codes
     */
    private Asset.AssetType determineAssetType(String exchange, String name) {
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
}