package com.porcana.domain.asset.service.personality;

import com.porcana.BaseIntegrationTest;
import com.porcana.domain.asset.dto.personality.AssetPersonality;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.personality.*;
import com.porcana.domain.asset.AssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "/sql/personality-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AssetPersonalityRuleEngineTest extends BaseIntegrationTest {

    @Autowired
    private AssetRepository assetRepository;

    // ETF IDs
    private static final UUID EQUITY_INDEX_ETF_LOW_RISK = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUITY_INDEX_ETF_MID_RISK = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID DIVIDEND_ETF = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID BOND_ETF_LOW_RISK = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID BOND_ETF_HIGH_RISK = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID COMMODITY_ETF = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID SECTOR_ETF = UUID.fromString("10000000-0000-0000-0000-000000000007");

    // Stock IDs
    private static final UUID HIGH_RISK_IT_STOCK = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MID_RISK_IT_STOCK = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID LOW_RISK_HEALTHCARE_STOCK = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID LOW_RISK_IT_STOCK = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID LOW_RISK_UTILITIES_STOCK = UUID.fromString("20000000-0000-0000-0000-000000000005");
    private static final UUID HIGH_RISK_IT_STOCK_2 = UUID.fromString("20000000-0000-0000-0000-000000000006");

    // Dividend Stock IDs
    private static final UUID HIGH_DIVIDEND_STOCK = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DIVIDEND_GROWTH_STOCK = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID MONTHLY_DIVIDEND_STOCK = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID INCOME_CORE_STOCK = UUID.fromString("30000000-0000-0000-0000-000000000004");

    @Nested
    @DisplayName("ETF 성격 판별")
    class EtfPersonalityTest {

        @Test
        @DisplayName("EQUITY_INDEX ETF (저위험) → CORE 역할, BROAD_INDEX 노출, STABLE 페르소나")
        void equityIndexEtf_lowRisk_shouldBeCoreStable() {
            // given
            Asset etf = assetRepository.findById(EQUITY_INDEX_ETF_LOW_RISK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.CORE);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.BROAD_INDEX);
            assertThat(personality.getPersona()).isEqualTo(Persona.STABLE);
        }

        @Test
        @DisplayName("EQUITY_INDEX ETF (중위험) → CORE 역할, GROWTH 페르소나")
        void equityIndexEtf_midRisk_shouldBeCoreGrowth() {
            // given
            Asset etf = assetRepository.findById(EQUITY_INDEX_ETF_MID_RISK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.CORE);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.BROAD_INDEX);
            // CORE 역할이면서 risk=3 → GROWTH 페르소나
            assertThat(personality.getPersona()).isEqualTo(Persona.GROWTH);
        }

        @Test
        @DisplayName("DIVIDEND ETF → INCOME 역할, 현금흐름형 페르소나")
        void dividendEtf_shouldBeIncome() {
            // given
            Asset etf = assetRepository.findById(DIVIDEND_ETF).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.INCOME);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.DIVIDEND);
            assertThat(personality.getPersona()).isEqualTo(Persona.CASHFLOW);
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.INCOME_CORE);
        }

        @Test
        @DisplayName("BOND ETF (저위험) → DEFENSIVE 역할, DEFENSIVE 페르소나")
        void bondEtf_lowRisk_shouldBeDefensive() {
            // given
            Asset etf = assetRepository.findById(BOND_ETF_LOW_RISK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.DEFENSIVE);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.BOND);
            // Role.DEFENSIVE → Persona.DEFENSIVE
            assertThat(personality.getPersona()).isEqualTo(Persona.DEFENSIVE);
        }

        @Test
        @DisplayName("BOND ETF (고위험) → HEDGE 역할")
        void bondEtf_highRisk_shouldBeHedge() {
            // given
            Asset etf = assetRepository.findById(BOND_ETF_HIGH_RISK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.HEDGE);
            assertThat(personality.getPersona()).isEqualTo(Persona.DEFENSIVE);
        }

        @Test
        @DisplayName("COMMODITY ETF → HEDGE 역할, 방어형 페르소나")
        void commodityEtf_shouldBeHedge() {
            // given
            Asset etf = assetRepository.findById(COMMODITY_ETF).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.HEDGE);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.COMMODITY);
            assertThat(personality.getPersona()).isEqualTo(Persona.DEFENSIVE);
        }

        @Test
        @DisplayName("SECTOR ETF → SATELLITE 역할, 테마형 페르소나")
        void sectorEtf_shouldBeSatellite() {
            // given
            Asset etf = assetRepository.findById(SECTOR_ETF).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(etf);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.SATELLITE);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.SECTOR);
            assertThat(personality.getPersona()).isEqualTo(Persona.THEMATIC);
        }
    }

    @Nested
    @DisplayName("주식 성격 판별")
    class StockPersonalityTest {

        @Test
        @DisplayName("고위험 주식 (risk >= 4) → GROWTH 역할, 공격형 페르소나")
        void highRiskStock_shouldBeGrowth() {
            // given
            Asset stock = assetRepository.findById(HIGH_RISK_IT_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.GROWTH);
            assertThat(personality.getExposureType()).isEqualTo(ExposureType.SINGLE_STOCK);
            assertThat(personality.getPersona()).isEqualTo(Persona.AGGRESSIVE);
            assertThat(personality.getRiskLevel()).isEqualTo(5);
        }

        @Test
        @DisplayName("중위험 주식 (risk = 3) → GROWTH 역할, 성장형 페르소나")
        void midRiskStock_shouldBeGrowth() {
            // given
            Asset stock = assetRepository.findById(MID_RISK_IT_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getRole()).isEqualTo(Role.GROWTH);
            assertThat(personality.getPersona()).isEqualTo(Persona.GROWTH);
            assertThat(personality.getRiskLevel()).isEqualTo(3);
        }

        @Test
        @DisplayName("저위험 방어 섹터 주식 (배당 친화적) → DEFENSIVE 역할, DEFENSIVE 페르소나")
        void lowRiskDefensiveSectorStock_shouldBeDefensive() {
            // given: 배당 친화적 섹터 + 저위험 → DIVIDEND_FOCUSED → DEFENSIVE 역할
            Asset stock = assetRepository.findById(LOW_RISK_HEALTHCARE_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            // 배당 친화적 섹터 + 저위험 → DIVIDEND_FOCUSED
            // DIVIDEND_FOCUSED + 방어 섹터 → DEFENSIVE 역할
            assertThat(personality.getRole()).isEqualTo(Role.DEFENSIVE);
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.DIVIDEND_FOCUSED);
            assertThat(personality.getPersona()).isEqualTo(Persona.DEFENSIVE);
        }

        @Test
        @DisplayName("저위험 일반 섹터 주식 → CORE 역할, BALANCED 페르소나")
        void lowRiskNormalSectorStock_shouldBeCore() {
            // given: IT 섹터는 배당 친화적이지 않음, 저위험 → HAS_DIVIDEND
            Asset stock = assetRepository.findById(LOW_RISK_IT_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            // IT 섹터, risk 1 → HAS_DIVIDEND → CORE 역할
            assertThat(personality.getRole()).isEqualTo(Role.CORE);
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.HAS_DIVIDEND);
            // risk <= 2 + HAS_DIVIDEND → BALANCED
            assertThat(personality.getPersona()).isEqualTo(Persona.BALANCED);
        }
    }

    @Nested
    @DisplayName("배당 프로필 판별")
    class DividendProfileTest {

        @Test
        @DisplayName("배당 친화적 섹터 + 저위험 → DIVIDEND_FOCUSED")
        void dividendFriendlySector_lowRisk_shouldBeDividendFocused() {
            // given
            Asset stock = assetRepository.findById(LOW_RISK_UTILITIES_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.DIVIDEND_FOCUSED);
        }

        @Test
        @DisplayName("일반 섹터 + 저위험 → HAS_DIVIDEND")
        void normalSector_lowRisk_shouldHaveDividend() {
            // given
            Asset stock = assetRepository.findById(LOW_RISK_IT_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.HAS_DIVIDEND);
        }

        @Test
        @DisplayName("고위험 주식 → NONE")
        void highRiskStock_shouldBeNone() {
            // given
            Asset stock = assetRepository.findById(HIGH_RISK_IT_STOCK_2).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.NONE);
        }

        @Test
        @DisplayName("배당 데이터가 있는 경우 - HIGH_DIVIDEND → INCOME_CORE")
        void withDividendData_highDividend_shouldBeIncomeCore() {
            // given
            Asset stock = assetRepository.findById(HIGH_DIVIDEND_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.INCOME_CORE);
            assertThat(personality.getRole()).isEqualTo(Role.INCOME);
        }

        @Test
        @DisplayName("배당 데이터가 있는 경우 - DIVIDEND_GROWTH → DIVIDEND_FOCUSED")
        void withDividendData_dividendGrowth_shouldBeDividendFocused() {
            // given
            Asset stock = assetRepository.findById(DIVIDEND_GROWTH_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.DIVIDEND_FOCUSED);
        }

        @Test
        @DisplayName("높은 배당수익률 + 월배당 → INCOME_CORE")
        void highYield_monthlyDividend_shouldBeIncomeCore() {
            // given
            Asset stock = assetRepository.findById(MONTHLY_DIVIDEND_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.INCOME_CORE);
        }
    }

    @Nested
    @DisplayName("순차 계산 검증")
    class SequentialCalculationTest {

        @Test
        @DisplayName("INCOME_CORE 배당 → INCOME 역할 → CASHFLOW 페르소나")
        void incomeCoreLeadsToIncomeRole() {
            // given
            Asset stock = assetRepository.findById(INCOME_CORE_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.INCOME_CORE);
            assertThat(personality.getRole()).isEqualTo(Role.INCOME);
            assertThat(personality.getPersona()).isEqualTo(Persona.CASHFLOW);
        }

        @Test
        @DisplayName("DIVIDEND_FOCUSED + 방어 섹터 → DEFENSIVE 역할")
        void dividendFocusedDefensiveSectorLeadsToDefensive() {
            // given
            Asset stock = assetRepository.findById(LOW_RISK_UTILITIES_STOCK).orElseThrow();

            // when
            AssetPersonality personality = AssetPersonalityRuleEngine.compute(stock);

            // then
            assertThat(personality.getDividendProfile()).isEqualTo(DividendProfile.DIVIDEND_FOCUSED);
            assertThat(personality.getRole()).isEqualTo(Role.DEFENSIVE);
        }
    }
}
