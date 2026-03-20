package com.porcana.domain.portfolio.service.deck;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.asset.dto.personality.AssetPersonality;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.personality.DividendProfile;
import com.porcana.domain.asset.entity.personality.Role;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.service.personality.AssetPersonalityRuleEngine;
import com.porcana.domain.portfolio.dto.deck.DeckAnalysis;
import com.porcana.domain.portfolio.dto.deck.PositionWithAsset;
import com.porcana.domain.portfolio.entity.deck.DeckSignal;
import com.porcana.domain.portfolio.entity.deck.DeckStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/sql/deck-analysis-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DeckAnalysisEngineTest extends BaseIntegrationTest {

    @Autowired
    private AssetRepository assetRepository;

    // 고위험 성장 주식
    private static final UUID NVDA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID META = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID AAPL = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID MSFT = UUID.fromString("a0000000-0000-0000-0000-000000000004");

    // CORE 역할 주식
    private static final UUID JPM = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID JNJ = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID PG = UUID.fromString("b0000000-0000-0000-0000-000000000003");

    // DEFENSIVE/HEDGE ETF
    private static final UUID BND = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final UUID GLD = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    private static final UUID TLT = UUID.fromString("c0000000-0000-0000-0000-000000000003");

    // INCOME ETF
    private static final UUID VYM = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID SCHD = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    // UTILITIES 주식
    private static final UUID NEE = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID SO = UUID.fromString("e0000000-0000-0000-0000-000000000002");

    // 한국 주식
    private static final UUID SAMSUNG = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID SK_HYNIX = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID NAVER = UUID.fromString("f0000000-0000-0000-0000-000000000003");

    // 배당 주식
    private static final UUID O = UUID.fromString("01000000-0000-0000-0000-000000000001");
    private static final UUID KO = UUID.fromString("01000000-0000-0000-0000-000000000002");
    private static final UUID INTC = UUID.fromString("01000000-0000-0000-0000-000000000003");

    // null 위험도 자산
    private static final UUID NEWCO = UUID.fromString("02000000-0000-0000-0000-000000000001");

    @Nested
    @DisplayName("빈 포트폴리오 처리")
    class EmptyPortfolioTest {

        @Test
        @DisplayName("null 포지션 → 빈 분석 결과")
        void nullPositions_shouldReturnEmptyAnalysis() {
            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(null);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.BALANCED);
            assertThat(analysis.getSignals()).isEmpty();
            assertThat(analysis.getMetrics().getAssetCount()).isZero();
        }

        @Test
        @DisplayName("빈 포지션 리스트 → 빈 분석 결과")
        void emptyPositions_shouldReturnEmptyAnalysis() {
            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(List.of());

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.BALANCED);
            assertThat(analysis.getSummary()).isEqualTo("포트폴리오에 자산이 없습니다");
        }
    }

    @Nested
    @DisplayName("덱 스타일 판별")
    class StyleDeterminationTest {

        @Test
        @DisplayName("공격형: 평균 위험도 4 이상 + 성장 비중 60% 이상")
        void aggressiveStyle() {
            // given: 고위험 성장 주식 중심
            List<PositionWithAsset> positions = List.of(
                    createPosition(NVDA, 40.0),  // GROWTH, risk=5
                    createPosition(META, 30.0),  // GROWTH, risk=4
                    createPosition(JPM, 20.0),   // CORE, risk=3
                    createPosition(NEE, 10.0)    // DEFENSIVE, risk=2
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.AGGRESSIVE);
            assertThat(analysis.getMetrics().getGrowthWeight()).isGreaterThanOrEqualTo(60);
            assertThat(analysis.getMetrics().getWeightedAverageRisk()).isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("현금흐름형: 인컴 비중 40% 이상")
        void cashflowStyle() {
            // given: 배당 ETF 중심
            List<PositionWithAsset> positions = List.of(
                    createPosition(VYM, 45.0),   // INCOME
                    createPosition(JPM, 35.0),   // CORE
                    createPosition(AAPL, 20.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.CASHFLOW);
            assertThat(analysis.getMetrics().getIncomeWeight()).isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("현금흐름형: 인컴 코어 비중 30% 이상")
        void cashflowStyle_byIncomeCoreWeight() {
            // given: INCOME_CORE 배당 자산 35%
            List<PositionWithAsset> positions = List.of(
                    createPosition(O, 35.0),     // INCOME_CORE 배당 프로필
                    createPosition(JPM, 35.0),   // CORE
                    createPosition(AAPL, 30.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.CASHFLOW);
            assertThat(analysis.getMetrics().getIncomeCoreWeight()).isGreaterThanOrEqualTo(30);
        }

        @Test
        @DisplayName("방어형: 방어 + 헤지 비중 40% 이상")
        void defensiveStyle() {
            // given: 채권/헤지 자산 중심
            List<PositionWithAsset> positions = List.of(
                    createPosition(BND, 30.0),   // DEFENSIVE
                    createPosition(GLD, 20.0),   // HEDGE
                    createPosition(PG, 30.0),    // CORE (Consumer Staples)
                    createPosition(AAPL, 20.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.DEFENSIVE);
            // defensiveWeight + hedgeWeight >= 40
            assertThat(analysis.getMetrics().getDefensiveWeight() + analysis.getMetrics().getHedgeWeight())
                    .isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("성장형: 성장 비중 50% 이상 (공격형 조건 미충족)")
        void growthStyle() {
            // given: 성장 비중 55% 이지만 평균 위험도 4 미만
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 30.0),  // GROWTH, risk=3
                    createPosition(META, 25.0),  // GROWTH, risk=4
                    createPosition(JPM, 25.0),   // CORE
                    createPosition(NEE, 20.0)    // DEFENSIVE
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: GROWTH 55% (AAPL 30 + META 25), avg risk < 4
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.GROWTH);
            assertThat(analysis.getMetrics().getGrowthWeight()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("테마형: 최다 섹터 비중 50% 이상")
        void thematicStyle() {
            // given: IT 섹터 집중 55%
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 30.0),  // IT
                    createPosition(MSFT, 25.0),  // IT
                    createPosition(NEE, 25.0),   // Utilities
                    createPosition(JPM, 20.0)    // Financials
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.THEMATIC);
            assertThat(analysis.getMetrics().getTopSector()).isEqualTo(Sector.INFORMATION_TECHNOLOGY);
            assertThat(analysis.getMetrics().getTopSectorWeight()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("균형형: 어떤 조건도 충족하지 않음")
        void balancedStyle() {
            // given: 균형 잡힌 포트폴리오 - 각 역할이 적절히 분산
            // GROWTH < 50%, INCOME < 40%, INCOME_CORE < 30%, DEFENSIVE+HEDGE < 40%, topSector < 50%
            // Role 분류: JPM(risk=3, Financials)=GROWTH, MSFT(risk=2, IT)=CORE,
            //           JNJ(risk=2, Healthcare)=DEFENSIVE, VYM(DIVIDEND ETF)=INCOME,
            //           GLD(COMMODITY ETF)=HEDGE, NEE(risk=2, Utilities)=DEFENSIVE
            List<PositionWithAsset> positions = List.of(
                    createPosition(JPM, 30.0),    // GROWTH, Financials (risk=3)
                    createPosition(MSFT, 25.0),   // CORE, IT (risk=2)
                    createPosition(JNJ, 15.0),    // DEFENSIVE, Healthcare (risk=2)
                    createPosition(VYM, 15.0),    // INCOME, Dividend ETF
                    createPosition(GLD, 10.0),    // HEDGE, Commodity ETF
                    createPosition(NEE, 5.0)      // DEFENSIVE, Utilities (risk=2)
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: 각 조건이 임계값 미만이어야 BALANCED
            // - GROWTH(30%) < 50%
            // - INCOME(15%) < 40%
            // - DEFENSIVE(20%) + HEDGE(10%) = 30% < 40%
            // - topSector(Financials 30%) < 50%
            assertThat(analysis.getMetrics().getGrowthWeight()).isLessThan(50);
            assertThat(analysis.getMetrics().getIncomeWeight()).isLessThan(40);
            assertThat(analysis.getMetrics().getDefensiveWeight() + analysis.getMetrics().getHedgeWeight()).isLessThan(40);
            assertThat(analysis.getMetrics().getTopSectorWeight()).isLessThan(50);
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.BALANCED);
        }
    }

    @Nested
    @DisplayName("시그널 감지")
    class SignalDetectionTest {

        @Test
        @DisplayName("섹터 집중: 최다 섹터 40% 이상")
        void sectorConcentration() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 45.0),  // IT
                    createPosition(JNJ, 35.0),   // Healthcare
                    createPosition(NEE, 20.0)    // Utilities
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getSignals()).contains(DeckSignal.SECTOR_CONCENTRATION);
        }

        @Test
        @DisplayName("고위험 노출: 성장 60% 이상 + 평균 위험 4 이상")
        void highRiskExposure() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(NVDA, 40.0),  // GROWTH, risk=5
                    createPosition(META, 30.0),  // GROWTH, risk=4
                    createPosition(JPM, 30.0)    // CORE, risk=3
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getSignals()).contains(DeckSignal.HIGH_RISK_EXPOSURE);
        }

        @Test
        @DisplayName("방어력 부족: 방어 + 헤지 비중 10% 이하")
        void lowDefense() {
            // given: 방어 자산 거의 없음
            List<PositionWithAsset> positions = List.of(
                    createPosition(NVDA, 50.0),  // GROWTH
                    createPosition(JPM, 30.0),   // CORE
                    createPosition(VYM, 20.0)    // INCOME
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getSignals()).contains(DeckSignal.LOW_DEFENSE);
        }

        @Test
        @DisplayName("종목 집중: 상위 3종목 55% 이상")
        void highConcentration() {
            // given: 7개 종목, 상위 3종목 60%
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 25.0),
                    createPosition(JNJ, 20.0),
                    createPosition(NEE, 15.0),
                    createPosition(VYM, 10.0),
                    createPosition(PG, 10.0),
                    createPosition(GLD, 10.0),
                    createPosition(JPM, 10.0)
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: top3 = 25 + 20 + 15 = 60%
            assertThat(analysis.getMetrics().getTop3Concentration()).isGreaterThanOrEqualTo(55);
            assertThat(analysis.getSignals()).contains(DeckSignal.HIGH_CONCENTRATION);
        }

        @Test
        @DisplayName("인컴 중심: 인컴 비중 40% 이상")
        void highIncome() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(VYM, 45.0),   // INCOME
                    createPosition(JPM, 35.0),   // CORE
                    createPosition(AAPL, 20.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getSignals()).contains(DeckSignal.HIGH_INCOME);
        }

        @Test
        @DisplayName("시장 집중: 미국 90% 이상")
        void marketConcentration() {
            // given: 미국 시장 100%
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 50.0),  // US
                    createPosition(JNJ, 50.0)    // US
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getUsWeight()).isGreaterThanOrEqualTo(90);
            assertThat(analysis.getSignals()).contains(DeckSignal.MARKET_CONCENTRATION);
        }

        @Test
        @DisplayName("시장 불균형: 미국 80% 이상 90% 미만")
        void marketImbalance() {
            // given: 미국 시장 85%
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 45.0),    // US
                    createPosition(JNJ, 40.0),     // US
                    createPosition(SAMSUNG, 15.0) // KR
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getUsWeight()).isGreaterThanOrEqualTo(80);
            assertThat(analysis.getMetrics().getUsWeight()).isLessThan(90);
            assertThat(analysis.getSignals()).contains(DeckSignal.MARKET_IMBALANCE);
            assertThat(analysis.getSignals()).doesNotContain(DeckSignal.MARKET_CONCENTRATION);
        }

        @Test
        @DisplayName("분산 부족: 5종목 미만 + 상위 3종목 60% 이상")
        void lowDiversification() {
            // given: 3개 종목만, top3 = 100%
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 40.0),
                    createPosition(JNJ, 30.0),
                    createPosition(NEE, 30.0)
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getAssetCount()).isLessThan(5);
            assertThat(analysis.getMetrics().getTop3Concentration()).isGreaterThanOrEqualTo(60);
            assertThat(analysis.getSignals()).contains(DeckSignal.LOW_DIVERSIFICATION);
        }

        @Test
        @DisplayName("배당 성향: 배당 중심 종목 비중 25% 이상")
        void dividendTilt() {
            // given: DIVIDEND_FOCUSED 30%
            List<PositionWithAsset> positions = List.of(
                    createPosition(KO, 30.0),    // DIVIDEND_FOCUSED
                    createPosition(JPM, 40.0),   // CORE
                    createPosition(AAPL, 30.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getDividendFocusedWeight()).isGreaterThanOrEqualTo(25);
            assertThat(analysis.getSignals()).contains(DeckSignal.DIVIDEND_TILT);
        }
    }

    @Nested
    @DisplayName("지표 계산")
    class MetricsCalculationTest {

        @Test
        @DisplayName("가중 평균 위험도 계산")
        void weightedAverageRisk() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(META, 50.0),  // risk=4
                    createPosition(MSFT, 50.0)   // risk=2
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: (4*50 + 2*50) / 100 = 3.0
            assertThat(analysis.getMetrics().getWeightedAverageRisk()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("위험도가 null인 자산 제외하고 계산")
        void weightedAverageRisk_excludeNullRisk() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(META, 50.0),   // risk=4
                    createPosition(NEWCO, 50.0)   // risk=null
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: null 제외하고 (4*50) / 50 = 4.0
            assertThat(analysis.getMetrics().getWeightedAverageRisk()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("ETF/주식 비중 계산")
        void etfStockWeight() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(VYM, 60.0),   // ETF
                    createPosition(JNJ, 40.0)    // STOCK
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getEtfWeight()).isEqualTo(60.0);
            assertThat(analysis.getMetrics().getStockWeight()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("미국/한국 비중 계산")
        void usKrWeight() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 70.0),    // US
                    createPosition(SAMSUNG, 30.0) // KR
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getUsWeight()).isEqualTo(70.0);
            assertThat(analysis.getMetrics().getKrWeight()).isEqualTo(30.0);
        }

        @Test
        @DisplayName("배당 관련 비중 계산")
        void dividendWeights() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(O, 30.0),     // INCOME_CORE
                    createPosition(KO, 25.0),    // DIVIDEND_FOCUSED
                    createPosition(INTC, 20.0),  // HAS_DIVIDEND
                    createPosition(AAPL, 25.0)   // NONE
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getIncomeCoreWeight()).isEqualTo(30.0);
            assertThat(analysis.getMetrics().getDividendFocusedWeight()).isEqualTo(25.0);
            // hasDividendWeight = INCOME_CORE + DIVIDEND_FOCUSED + HAS_DIVIDEND = 30 + 25 + 20 = 75
            assertThat(analysis.getMetrics().getHasDividendWeight()).isEqualTo(75.0);
        }

        @Test
        @DisplayName("헤지 비중 별도 계산")
        void hedgeWeight() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(GLD, 20.0),   // HEDGE
                    createPosition(NEE, 30.0),   // DEFENSIVE
                    createPosition(AAPL, 50.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getMetrics().getHedgeWeight()).isEqualTo(20.0);
            assertThat(analysis.getMetrics().getDefensiveWeight()).isEqualTo(30.0);
        }
    }

    @Nested
    @DisplayName("텍스트 해설 생성")
    class TextGenerationTest {

        @Test
        @DisplayName("분석 결과에 텍스트 해설 포함")
        void analysisIncludesText() {
            // given
            List<PositionWithAsset> positions = List.of(
                    createPosition(AAPL, 40.0),
                    createPosition(JNJ, 30.0),
                    createPosition(NEE, 30.0)
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then
            assertThat(analysis.getSummary()).isNotBlank();
            assertThat(analysis.getStrengths()).isNotEmpty();
            assertThat(analysis.getWeaknesses()).isNotEmpty();
            assertThat(analysis.getTips()).isNotEmpty();
            assertThat(analysis.getInvestorFit()).isNotBlank();
        }

        @Test
        @DisplayName("배당 관련 해설 포함")
        void dividendTextIncluded() {
            // given: 인컴 코어 35% 이상
            List<PositionWithAsset> positions = List.of(
                    createPosition(O, 35.0),     // INCOME_CORE
                    createPosition(JPM, 35.0),   // CORE
                    createPosition(AAPL, 30.0)   // GROWTH
            );

            // when
            DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);

            // then: CASHFLOW 스타일이어야 함
            assertThat(analysis.getStyle()).isEqualTo(DeckStyle.CASHFLOW);
            assertThat(analysis.getSummary()).contains("배당");
        }
    }

    // Helper methods

    private PositionWithAsset createPosition(UUID assetId, Double weightPct) {
        Asset asset = assetRepository.findById(assetId).orElseThrow();
        AssetPersonality personality = AssetPersonalityRuleEngine.compute(asset);
        return new PositionWithAsset(asset, weightPct, personality);
    }
}
