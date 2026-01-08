package com.porcana.batch.provider.kr;

import com.porcana.domain.asset.entity.UniverseTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Provider for universe tagging based on CSV files
 * Reads KOSPI200, KOSDAQ150 constituent lists from CSV
 */
@Slf4j
@Component
public class UniverseTaggingProvider {

    private static final String KOSPI200_CSV = "batch/kospi200.csv";
    private static final String KOSDAQ150_CSV = "batch/kosdaq150.csv";

    /**
     * Read KOSPI200 constituent symbols from CSV
     *
     * @return Set of stock symbols in KOSPI200
     */
    public Set<String> readKospi200Constituents() {
        return readCsvSymbols(KOSPI200_CSV, UniverseTag.KOSPI200);
    }

    /**
     * Read KOSDAQ150 constituent symbols from CSV
     *
     * @return Set of stock symbols in KOSDAQ150
     */
    public Set<String> readKosdaq150Constituents() {
        return readCsvSymbols(KOSDAQ150_CSV, UniverseTag.KOSDAQ150);
    }

    /**
     * Read symbols from CSV file
     * Expected format: one symbol per line, or CSV with symbol in first column
     *
     * @param csvPath Path to CSV file in resources
     * @param tag Universe tag for logging
     * @return Set of stock symbols
     */
    private Set<String> readCsvSymbols(String csvPath, UniverseTag tag) {
        Set<String> symbols = new HashSet<>();

        try {
            ClassPathResource resource = new ClassPathResource(csvPath);
            if (!resource.exists()) {
                log.warn("CSV file not found: {}. Skipping {} tagging.", csvPath, tag);
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

                    // Trim and skip empty lines
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

            log.info("Read {} symbols for {} from {}", symbols.size(), tag, csvPath);

        } catch (IOException e) {
            log.error("Failed to read CSV file: {}", csvPath, e);
        }

        return symbols;
    }
}