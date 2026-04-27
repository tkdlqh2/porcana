package com.porcana.batch.provider.us;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fetches US stock universe constituent lists from Wikipedia.
 *
 * Covers S&P 500 (quarterly rebalancing) and NASDAQ 100 (annual rebalancing).
 * Dow Jones 30 is read from dowjones.csv instead — it changes only a few times per decade,
 * so manual CSV updates are more reliable than scraping.
 *
 * Parse failures return an empty set so the calling job can skip safely without data loss.
 */
@Slf4j
@Component
public class WikipediaUniverseProvider {

    private static final String SP500_URL =
            "https://en.wikipedia.org/wiki/List_of_S%26P_500_companies";
    private static final String NASDAQ100_URL =
            "https://en.wikipedia.org/wiki/Nasdaq-100";

    private static final int TIMEOUT_MS = 20_000;
    private static final int SP500_MIN_SYMBOLS     = 490;
    private static final int NASDAQ100_MIN_SYMBOLS = 90;
    private static final int NASDAQ100_MAX_SYMBOLS = 110;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public Set<String> fetchSp500Symbols() {
        try {
            Document doc = Jsoup.connect(SP500_URL).timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; Porcana/1.0)")
                    .get();

            // The S&P 500 page has a stable table with id="constituents"
            Element table = doc.getElementById("constituents");
            if (table == null) {
                log.warn("S&P 500 table (id=constituents) not found — HTML structure may have changed");
                return Set.of();
            }

            Set<String> symbols = new LinkedHashSet<>();
            for (Element row : table.select("tbody tr")) {
                Elements cells = row.select("td");
                if (!cells.isEmpty()) {
                    String symbol = normalizeSymbol(cells.get(0).text());
                    if (isValidTicker(symbol)) symbols.add(symbol);
                }
            }

            if (symbols.size() < SP500_MIN_SYMBOLS) {
                log.warn("S&P 500 fetch returned only {} symbols (expected ≥ {}). Discarding.",
                        symbols.size(), SP500_MIN_SYMBOLS);
                return Set.of();
            }

            log.info("Fetched {} S&P 500 symbols from Wikipedia", symbols.size());
            return symbols;

        } catch (Exception e) {
            log.error("Failed to fetch S&P 500 symbols from Wikipedia", e);
            return Set.of();
        }
    }

    public Set<String> fetchNasdaq100Symbols() {
        try {
            Document doc = Jsoup.connect(NASDAQ100_URL).timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; Porcana/1.0)")
                    .get();

            // Try common header keywords in order
            for (String keyword : new String[]{"ticker", "symbol"}) {
                Set<String> symbols = parseWikitableByHeaderKeyword(doc, keyword, NASDAQ100_MIN_SYMBOLS, NASDAQ100_MAX_SYMBOLS);
                if (!symbols.isEmpty()) {
                    log.info("Fetched {} NASDAQ 100 symbols from Wikipedia", symbols.size());
                    return symbols;
                }
            }

            log.warn("NASDAQ 100 symbols could not be parsed from Wikipedia. HTML structure may have changed.");
            return Set.of();

        } catch (Exception e) {
            log.error("Failed to fetch NASDAQ 100 symbols from Wikipedia", e);
            return Set.of();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Scans all wikitables on the page for one whose header contains {@code headerKeyword},
     * then extracts that column's values if the result is within the expected size range.
     * The upper bound guards against accidentally picking history/change tables that happen
     * to contain a "ticker" column but have far more rows than the current constituent list.
     */
    private Set<String> parseWikitableByHeaderKeyword(Document doc, String headerKeyword, int minExpected, int maxExpected) {
        for (Element table : doc.select("table.wikitable")) {
            int colIndex = findColumnIndex(table, headerKeyword);
            if (colIndex < 0) continue;

            Set<String> symbols = extractColumnValues(table, colIndex);
            if (symbols.size() >= minExpected && symbols.size() <= maxExpected) return symbols;
        }
        return new LinkedHashSet<>();
    }

    /**
     * Searches ALL rows in the table for a {@code <th>} cell containing {@code keyword}.
     * Scanning all rows (not just the first row) handles tables where the header row
     * is preceded by a caption or colspan row.
     */
    private int findColumnIndex(Element table, String keyword) {
        for (Element row : table.select("tr")) {
            Elements cells = row.select("th");
            if (cells.isEmpty()) continue;
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).text().toLowerCase().contains(keyword)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extracts normalized ticker symbols from {@code colIndex} of all data rows.
     * Header rows (th-only) are skipped because they have no td cells.
     * Non-ticker values (dates, descriptions, etc.) are filtered by {@link #isValidTicker}.
     */
    private Set<String> extractColumnValues(Element table, int colIndex) {
        Set<String> values = new LinkedHashSet<>();
        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() > colIndex) {
                String symbol = normalizeSymbol(cells.get(colIndex).text());
                if (isValidTicker(symbol)) values.add(symbol);
            }
        }
        return values;
    }

    /** Ticker symbols: 1–5 uppercase letters, optionally followed by a hyphen and 1–2 letters (e.g. BRK-B). */
    private boolean isValidTicker(String s) {
        return s != null && s.matches("[A-Z]{1,5}(-[A-Z]{1,2})?");
    }

    /** Trim, uppercase, and replace dots with hyphens (BRK.B → BRK-B, as FMP uses hyphens). */
    private String normalizeSymbol(String raw) {
        return raw.trim().replace(".", "-").toUpperCase();
    }
}