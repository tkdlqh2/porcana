package com.porcana.batch.provider;

import com.porcana.batch.dto.AssetBatchDto;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider for ETF data from CSV files
 * Reads ETF symbols, names, and asset classes from CSV
 */
@Slf4j
@Component
public class EtfProvider {

    private static final String KR_ETF_CSV = "batch/kr_etf.csv";
    private static final String US_ETF_CSV = "batch/us_etf.csv";

    /**
     * Read Korean ETF data from CSV
     * Format: symbol, name, asset_class
     *
     * @return List of AssetBatchDto for Korean ETFs
     */
    public List<AssetBatchDto> readKrEtfs() {
        return readEtfCsv(KR_ETF_CSV, Asset.Market.KR);
    }

    /**
     * Read US ETF data from CSV
     * Format: symbol, name, asset_class
     *
     * @return List of AssetBatchDto for US ETFs
     */
    public List<AssetBatchDto> readUsEtfs() {
        return readEtfCsv(US_ETF_CSV, Asset.Market.US);
    }

    /**
     * Read ETF data from CSV file
     * Expected CSV format: symbol, name, asset_class
     *
     * @param csvPath CSV file path in resources
     * @param market Market (KR or US)
     * @return List of AssetBatchDto
     */
    private List<AssetBatchDto> readEtfCsv(String csvPath, Asset.Market market) {
        List<AssetBatchDto> etfs = new ArrayList<>();
        LocalDate asOf = LocalDate.now();

        try {
            ClassPathResource resource = new ClassPathResource(csvPath);
            if (!resource.exists()) {
                log.warn("CSV file not found: {}. Skipping ETF processing.", csvPath);
                return etfs;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean isFirstLine = true;

                while ((line = reader.readLine()) != null) {
                    // Skip header line
                    if (isFirstLine) {
                        isFirstLine = false;
                        continue;
                    }

                    // Trim and skip empty lines
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse CSV: symbol, name, asset_class
                    String[] parts = line.split(",");
                    if (parts.length < 3) {
                        log.warn("Invalid CSV line (expected 3 columns): {}", line);
                        continue;
                    }

                    String symbol = parts[0].trim();
                    String name = parts[1].trim();
                    String assetClassStr = parts[2].trim();

                    // Parse asset class
                    AssetClass assetClass;
                    try {
                        assetClass = AssetClass.valueOf(assetClassStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid asset class '{}' for symbol {}. Skipping.", assetClassStr, symbol);
                        continue;
                    }

                    // Build DTO
                    AssetBatchDto dto = AssetBatchDto.builder()
                            .market(market)
                            .symbol(symbol)
                            .name(name)
                            .type(Asset.AssetType.ETF)
                            .assetClass(assetClass)
                            .active(true) // ETFs in CSV are active by default
                            .asOf(asOf)
                            .build();

                    etfs.add(dto);
                }
            }

            log.info("Read {} ETFs from {}", etfs.size(), csvPath);

        } catch (IOException e) {
            log.error("Failed to read ETF CSV file: {}", csvPath, e);
        }

        return etfs;
    }
}
