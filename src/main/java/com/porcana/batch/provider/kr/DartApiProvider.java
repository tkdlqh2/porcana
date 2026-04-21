package com.porcana.batch.provider.kr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendDataStatus;
import com.porcana.domain.asset.entity.DividendFrequency;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DART (금융감독원 전자공시) API Provider for Korean stock dividend data
 *
 * Flow:
 * 1. Download corp_code mapping from DART (ZIP → CORPCODE.xml)
 * 2. For each KR stock symbol, look up corp_code
 * 3. Query alotMatter API for dividend data
 */
@Slf4j
@Component
public class DartApiProvider {

    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml";
    private static final String ALOT_MATTER_URL = "https://opendart.fss.or.kr/api/alotMatter.json";
    private static final String REPRT_CODE_ANNUAL = "11011"; // 사업보고서

    // 배당수익률 임계값 (소수 기준)
    private static final BigDecimal HIGH_DIVIDEND_THRESHOLD = new BigDecimal("0.04"); // 4%
    private static final BigDecimal MID_DIVIDEND_THRESHOLD = new BigDecimal("0.02");  // 2%

    private final RestTemplate restTemplate;
    private final String apiKey;

    // corp_code 매핑: KR 종목코드(6자리) → DART corp_code
    private Map<String, String> corpCodeMap = new HashMap<>();
    private LocalDate corpCodeMapUpdatedAt;

    public DartApiProvider(
            RestTemplate restTemplate,
            @Value("${batch.provider.dart.api-key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * KR 종목의 배당 데이터를 DART API로부터 조회
     *
     * @param stockCode KR 종목코드 (6자리, 예: "005930")
     * @param currentPrice 현재 주가 (배당수익률 계산용 fallback)
     * @return DividendData 또는 null (데이터 없음)
     */
    public DividendData fetchDividendData(String stockCode, BigDecimal currentPrice) {
        if (!isConfigured()) {
            log.warn("DART API key not configured. Skipping dividend fetch for {}", stockCode);
            return null;
        }

        try {
            ensureCorpCodeMapLoaded();

            String corpCode = corpCodeMap.get(stockCode);
            if (corpCode == null) {
                log.debug("No corp_code found for stock: {}", stockCode);
                return null;
            }

            return fetchAlotMatter(stockCode, corpCode, currentPrice);

        } catch (Exception e) {
            log.warn("Failed to fetch DART dividend data for {}: {}", stockCode, e.getMessage());
            return null;
        }
    }

    /**
     * corp_code 매핑 로드 (하루에 한 번만)
     */
    private synchronized void ensureCorpCodeMapLoaded() throws Exception {
        LocalDate today = LocalDate.now();
        if (corpCodeMapUpdatedAt != null && corpCodeMapUpdatedAt.equals(today) && !corpCodeMap.isEmpty()) {
            return;
        }

        log.info("Loading DART corp_code mapping from API...");
        Map<String, String> downloaded = downloadCorpCodeMap();
        if (downloaded.isEmpty()) {
            throw new IllegalStateException("DART corp_code mapping is empty — keeping existing cache");
        }
        corpCodeMap = downloaded;
        corpCodeMapUpdatedAt = today;
        log.info("Loaded {} corp_code entries from DART", corpCodeMap.size());
    }

    /**
     * DART에서 corp_code ZIP 파일을 다운로드하고 파싱
     * stockCode(6자리) → corpCode 매핑 반환
     */
    private Map<String, String> downloadCorpCodeMap() throws Exception {
        String url = CORP_CODE_URL + "?crtfc_key=" + apiKey;

        byte[] zipBytes = restTemplate.getForObject(url, byte[].class);
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalStateException("DART corp_code ZIP response is empty");
        }

        Map<String, String> map = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().toLowerCase().contains("corpcode") &&
                        entry.getName().toLowerCase().endsWith(".xml")) {

                    byte[] xmlBytes = zis.readAllBytes();
                    map = parseCorpCodeXml(xmlBytes);
                    break;
                }
            }
        }

        return map;
    }

    /**
     * CORPCODE.xml 파싱하여 stockCode → corpCode 매핑 반환
     */
    private Map<String, String> parseCorpCodeXml(byte[] xmlBytes) throws Exception {
        Map<String, String> map = new HashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));

        NodeList items = doc.getElementsByTagName("list");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String corpCode = getTagValue("corp_code", item);
            String stockCode = getTagValue("stock_code", item);

            // stock_code가 있는 항목만 (상장법인)
            if (stockCode != null && !stockCode.isBlank()) {
                map.put(stockCode.trim(), corpCode.trim());
            }
        }

        return map;
    }

    private String getTagValue(String tagName, Element element) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    /**
     * DART alotMatter API 조회 (배당에 관한 사항)
     * 전년도 사업보고서 기준
     */
    private DividendData fetchAlotMatter(String stockCode, String corpCode, BigDecimal currentPrice) {
        int bsnsYear = LocalDate.now().getYear() - 1;

        String url = String.format("%s?crtfc_key=%s&corp_code=%s&bsns_year=%d&reprt_code=%s",
                ALOT_MATTER_URL, apiKey, corpCode, bsnsYear, REPRT_CODE_ANNUAL);

        try {
            AlotMatterResponse response = restTemplate.getForObject(url, AlotMatterResponse.class);

            if (response == null || !"000".equals(response.getStatus())) {
                log.warn("DART API error for stock {} (corp_code: {}): status={}",
                        stockCode, corpCode, response != null ? response.getStatus() : "null");
                return null;
            }
            if (response.getList() == null) {
                log.debug("No alotMatter data for stock {} (corp_code: {})", stockCode, corpCode);
                return DividendData.noDividend();
            }

            return parseAlotMatterData(response.getList(), stockCode, currentPrice, bsnsYear);

        } catch (Exception e) {
            log.debug("Failed to fetch alotMatter for {} ({}): {}", stockCode, corpCode, e.getMessage());
            return null;
        }
    }

    /**
     * alotMatter 응답 파싱하여 DividendData 생성
     */
    private DividendData parseAlotMatterData(List<AlotMatterItem> items, String stockCode,
                                             BigDecimal currentPrice, int bsnsYear) {
        BigDecimal dividendYield = null;
        BigDecimal perShareDividend = null;

        for (AlotMatterItem item : items) {
            String sjNm = item.getSjNm();
            String thstrm = item.getThstrm();
            if (sjNm == null || thstrm == null || thstrm.isBlank() || "-".equals(thstrm.trim())) {
                continue;
            }

            // 현금배당수익률 (%) 직접 파싱
            if (sjNm.contains("현금배당수익률")) {
                dividendYield = parsePercentToDecimal(thstrm);
            }
            // 주당 현금배당금(원) - fallback
            else if (sjNm.contains("주당") && sjNm.contains("현금배당금")) {
                perShareDividend = parseAmount(thstrm);
            }
        }

        // 배당수익률을 직접 못 가져온 경우 주당배당금 / 현재주가로 계산
        if (dividendYield == null && perShareDividend != null &&
                currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            dividendYield = perShareDividend.divide(currentPrice, 6, RoundingMode.HALF_UP);
            log.debug("Calculated dividend yield for {}: {} / {} = {}",
                    stockCode, perShareDividend, currentPrice, dividendYield);
        }

        if (dividendYield == null || dividendYield.compareTo(BigDecimal.ZERO) <= 0) {
            return DividendData.noDividend();
        }

        DividendCategory category = determineDividendCategory(dividendYield);
        LocalDate lastDividendDate = LocalDate.of(bsnsYear, 12, 31); // KR 대부분 회계연도 말 배당

        return DividendData.builder()
                .dividendAvailable(true)
                .dividendYield(dividendYield)
                .dividendFrequency(DividendFrequency.ANNUAL) // KR은 연간 배당이 기본
                .dividendCategory(category)
                .dividendDataStatus(DividendDataStatus.VERIFIED)
                .lastDividendDate(lastDividendDate)
                .build();
    }

    /**
     * "2.5" or "2.50%" → 0.025 (소수)
     */
    private BigDecimal parsePercentToDecimal(String value) {
        try {
            String cleaned = value.replace("%", "").replace(",", "").trim();
            BigDecimal pct = new BigDecimal(cleaned);
            return pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.debug("Failed to parse percent: {}", value);
            return null;
        }
    }

    /**
     * "1,200" or "1200" → BigDecimal
     */
    private BigDecimal parseAmount(String value) {
        try {
            String cleaned = value.replace(",", "").trim();
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            log.debug("Failed to parse amount: {}", value);
            return null;
        }
    }

    private DividendCategory determineDividendCategory(BigDecimal dividendYield) {
        if (dividendYield.compareTo(HIGH_DIVIDEND_THRESHOLD) >= 0) {
            return DividendCategory.HIGH_DIVIDEND;
        } else if (dividendYield.compareTo(MID_DIVIDEND_THRESHOLD) >= 0) {
            return DividendCategory.DIVIDEND_GROWTH;
        } else {
            return DividendCategory.HAS_DIVIDEND;
        }
    }

    /**
     * 배당 데이터 결과
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

        public static DividendData noDividend() {
            return DividendData.builder()
                    .dividendAvailable(false)
                    .dividendYield(null)
                    .dividendFrequency(DividendFrequency.NONE)
                    .dividendCategory(DividendCategory.NONE)
                    .dividendDataStatus(DividendDataStatus.VERIFIED)
                    .lastDividendDate(null)
                    .build();
        }
    }

    /**
     * DART alotMatter API 응답
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AlotMatterResponse {
        @JsonProperty("status")
        private String status;

        @JsonProperty("message")
        private String message;

        @JsonProperty("list")
        private List<AlotMatterItem> list;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AlotMatterItem {
        @JsonProperty("sj_nm")
        private String sjNm; // 항목명

        @JsonProperty("thstrm")
        private String thstrm; // 당기

        @JsonProperty("frmtrm")
        private String frmtrm; // 전기

        @JsonProperty("corp_code")
        private String corpCode;

        @JsonProperty("corp_name")
        private String corpName;
    }
}
