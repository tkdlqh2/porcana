package com.porcana.batch.provider.us;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.UniverseTag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of US asset data provider using Financial Modeling Prep (FMP) API
 * Fetches S&P 500 constituent data
 */
@Slf4j
@Component
public class FmpAssetProvider implements UsAssetDataProvider {

    private static final String SP500_ENDPOINT = "/api/v3/sp500_constituent";

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
        log.info("Fetching S&P 500 constituents from FMP");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FMP API key not configured. Skipping S&P 500 fetch.");
            return new ArrayList<>();
        }

        try {
            String url = String.format("%s%s?apikey=%s", baseUrl, SP500_ENDPOINT, apiKey);

            FmpConstituent[] response = restTemplate.getForObject(url, FmpConstituent[].class);

            if (response == null || response.length == 0) {
                log.warn("No S&P 500 constituents returned from FMP");
                return new ArrayList<>();
            }

            List<AssetBatchDto> assets = new ArrayList<>();
            LocalDate asOf = LocalDate.now();

            for (FmpConstituent constituent : response) {
                assets.add(convertToDto(constituent, asOf));
            }

            log.info("Fetched {} S&P 500 constituents from FMP", assets.size());
            return assets;

        } catch (Exception e) {
            log.error("Failed to fetch S&P 500 constituents from FMP", e);
            throw new AssetDataProviderException("Failed to fetch US market assets from FMP", e);
        }
    }

    @Override
    public String getProviderName() {
        return "FMP";
    }

    /**
     * Convert FMP API response to AssetBatchDto
     */
    private AssetBatchDto convertToDto(FmpConstituent constituent, LocalDate asOf) {
        return AssetBatchDto.builder()
                .market(Asset.Market.US)
                .symbol(constituent.getSymbol())
                .exchange(determineExchange(constituent))
                .name(constituent.getName())
                .type(Asset.AssetType.STOCK)
                .universeTags(List.of(UniverseTag.SP500)) // S&P 500 constituents
                .active(true) // S&P 500 constituents are active by default
                .asOf(asOf)
                .build();
    }

    /**
     * Determine exchange from constituent data
     * FMP doesn't always provide exchange in constituent endpoint,
     * so we use a simple heuristic or default to "NYSE"
     */
    private String determineExchange(FmpConstituent constituent) {
        // If exchange is provided in the response, use it
        // Otherwise, default to NYSE (most S&P 500 stocks are on NYSE)
        // This can be enhanced with a separate API call if needed
        return "NYSE"; // TODO: Enhance with actual exchange lookup if needed
    }

    /**
     * FMP S&P 500 Constituent Response
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FmpConstituent {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("name")
        private String name;

        @JsonProperty("sector")
        private String sector;

        @JsonProperty("subSector")
        private String subSector;

        @JsonProperty("headQuarter")
        private String headQuarter;

        @JsonProperty("dateFirstAdded")
        private String dateFirstAdded;

        @JsonProperty("cik")
        private String cik;

        @JsonProperty("founded")
        private String founded;
    }
}