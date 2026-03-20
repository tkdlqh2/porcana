package com.porcana.domain.portfolio.dto.deck;

import com.porcana.domain.portfolio.entity.deck.DeckSignal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * 덱 분석 API 응답 DTO
 */
@Getter
@Builder
@Schema(description = "포트폴리오 덱 분석 결과")
public class DeckAnalysisResponse {

    @Schema(description = "포트폴리오 ID")
    private final UUID portfolioId;

    // === 스타일 ===
    @Schema(description = "덱 스타일", example = "BALANCED")
    private final String style;

    @Schema(description = "덱 스타일 한글명", example = "균형형")
    private final String styleDisplayName;

    @Schema(description = "덱 스타일 설명")
    private final String styleDescription;

    // === 텍스트 해설 ===
    @Schema(description = "한 줄 요약")
    private final String summary;

    @Schema(description = "강점 목록 (최대 2개)")
    private final List<String> strengths;

    @Schema(description = "약점 목록 (최대 2개)")
    private final List<String> weaknesses;

    @Schema(description = "운영 팁 목록 (최대 2개)")
    private final List<String> tips;

    @Schema(description = "투자자 적합성")
    private final String investorFit;

    // === 지표 ===
    @Schema(description = "가중 평균 위험도 (1.0-5.0)", example = "3.2")
    private final Double weightedAverageRisk;

    @Schema(description = "최다 섹터")
    private final String topSector;

    @Schema(description = "최다 섹터 한글명")
    private final String topSectorKorean;

    @Schema(description = "최다 섹터 비중 (%)", example = "35.5")
    private final Double topSectorWeight;

    @Schema(description = "성장형 비중 (%)", example = "45.0")
    private final Double growthWeight;

    @Schema(description = "인컴형 비중 (%)", example = "20.0")
    private final Double incomeWeight;

    @Schema(description = "방어형 비중 (%)", example = "15.0")
    private final Double defensiveWeight;

    @Schema(description = "핵심형 비중 (%)", example = "20.0")
    private final Double coreWeight;

    @Schema(description = "ETF 비중 (%)", example = "40.0")
    private final Double etfWeight;

    @Schema(description = "개별주식 비중 (%)", example = "60.0")
    private final Double stockWeight;

    @Schema(description = "미국 시장 비중 (%)", example = "70.0")
    private final Double usWeight;

    @Schema(description = "한국 시장 비중 (%)", example = "30.0")
    private final Double krWeight;

    @Schema(description = "상위 3종목 집중도 (%)", example = "45.0")
    private final Double top3Concentration;

    @Schema(description = "총 종목 수", example = "10")
    private final Integer assetCount;

    // === 시그널 ===
    @Schema(description = "감지된 시그널 목록")
    private final List<SignalInfo> signals;

    /**
     * 시그널 정보
     */
    @Getter
    @Builder
    @Schema(description = "시그널 정보")
    public static class SignalInfo {
        @Schema(description = "시그널 코드", example = "SECTOR_CONCENTRATION")
        private final String signal;

        @Schema(description = "시그널 한글명", example = "섹터 집중")
        private final String displayName;

        @Schema(description = "시그널 설명")
        private final String description;

        public static SignalInfo from(DeckSignal signal) {
            return SignalInfo.builder()
                    .signal(signal.name())
                    .displayName(signal.getDisplayName())
                    .description(signal.getDescription())
                    .build();
        }
    }

    /**
     * DeckAnalysis로부터 Response 생성
     */
    public static DeckAnalysisResponse from(UUID portfolioId, DeckAnalysis analysis) {
        DeckMetrics metrics = analysis.getMetrics();

        return DeckAnalysisResponse.builder()
                .portfolioId(portfolioId)
                // 스타일
                .style(analysis.getStyle().name())
                .styleDisplayName(analysis.getStyle().getDisplayName())
                .styleDescription(analysis.getStyle().getDescription())
                // 텍스트 해설
                .summary(analysis.getSummary())
                .strengths(analysis.getStrengths())
                .weaknesses(analysis.getWeaknesses())
                .tips(analysis.getTips())
                .investorFit(analysis.getInvestorFit())
                // 지표
                .weightedAverageRisk(metrics.getWeightedAverageRisk())
                .topSector(metrics.getTopSector() != null ? metrics.getTopSector().name() : null)
                .topSectorKorean(metrics.getTopSector() != null ? metrics.getTopSector().getKoreanName() : null)
                .topSectorWeight(metrics.getTopSectorWeight())
                .growthWeight(metrics.getGrowthWeight())
                .incomeWeight(metrics.getIncomeWeight())
                .defensiveWeight(metrics.getDefensiveWeight())
                .coreWeight(metrics.getCoreWeight())
                .etfWeight(metrics.getEtfWeight())
                .stockWeight(metrics.getStockWeight())
                .usWeight(metrics.getUsWeight())
                .krWeight(metrics.getKrWeight())
                .top3Concentration(metrics.getTop3Concentration())
                .assetCount(metrics.getAssetCount())
                // 시그널
                .signals(analysis.getSignals().stream()
                        .map(SignalInfo::from)
                        .toList())
                .build();
    }
}
