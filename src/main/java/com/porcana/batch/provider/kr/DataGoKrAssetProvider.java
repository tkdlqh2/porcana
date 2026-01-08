package com.porcana.batch.provider.kr;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.domain.asset.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Korean asset data provider using data.go.kr API
 * Fetches all listed stocks from Korean public data portal
 */
@Slf4j
@Component
public class DataGoKrAssetProvider implements KrAssetDataProvider {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String apiUrl;

    public DataGoKrAssetProvider(
            RestTemplate restTemplate,
            @Value("${batch.provider.kr.api-key:}") String apiKey,
            @Value("${batch.provider.kr.api-url:https://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService}") String apiUrl
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    @Override
    public List<AssetBatchDto> fetchAssets() throws AssetDataProviderException {
        log.info("Fetching Korean market assets from data.go.kr");

        try {
            // TODO: Implement actual API call to data.go.kr
            // For now, return empty list to prevent errors during development
            // This should be implemented with proper API integration

            List<AssetBatchDto> assets = new ArrayList<>();

            // Example of what the implementation should look like:
            // String url = String.format("%s/getStockPriceInfo?serviceKey=%s&numOfRows=5000&pageNo=1&resultType=json",
            //     apiUrl, apiKey);
            // ResponseEntity<DataGoKrResponse> response = restTemplate.getForEntity(url, DataGoKrResponse.class);
            //
            // if (response.getBody() != null && response.getBody().getItems() != null) {
            //     for (DataGoKrItem item : response.getBody().getItems()) {
            //         assets.add(convertToDto(item));
            //     }
            // }

            log.info("Fetched {} assets from data.go.kr", assets.size());
            return assets;

        } catch (Exception e) {
            log.error("Failed to fetch assets from data.go.kr", e);
            throw new AssetDataProviderException("Failed to fetch Korean market assets from data.go.kr", e);
        }
    }

    @Override
    public String getProviderName() {
        return "DATA_GO_KR";
    }

    /**
     * Convert data.go.kr API response to AssetBatchDto
     * This method should be implemented based on actual API response structure
     */
    private AssetBatchDto convertToDto(Object item) {
        // TODO: Implement actual conversion based on API response
        // Example structure:
        return AssetBatchDto.builder()
                .market(Asset.Market.KR)
                .symbol("") // from item.getStockCode()
                .exchange("") // from item.getMarketName() - KOSPI/KOSDAQ/ETF
                .name("") // from item.getCorpName()
                .type(Asset.AssetType.STOCK) // determine from exchange
                .active(false) // will be set to true by universe tagging
                .asOf(LocalDate.now())
                .build();
    }
}