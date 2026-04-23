package com.porcana.domain.portfolio.service;

import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioReturnCalculatorTest {

    @Mock
    private PortfolioDailyReturnRepository portfolioDailyReturnRepository;

    @Mock
    private SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;

    @InjectMocks
    private PortfolioReturnCalculator calculator;

    private static final UUID PORTFOLIO_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_A = UUID.randomUUID();
    private static final UUID SNAPSHOT_B = UUID.randomUUID();
    private static final UUID ASSET_1 = UUID.randomUUID();
    private static final UUID ASSET_2 = UUID.randomUUID();

    @Nested
    @DisplayName("calculateTotalReturn")
    class CalculateTotalReturnTest {

        @Test
        void singleSnapshot_shouldUseOnlyLastDayReturn() {
            List<PortfolioDailyReturn> dailyReturns = List.of(
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 11), new BigDecimal("2.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 12), new BigDecimal("5.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 13), new BigDecimal("10.0"))
            );

            when(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(dailyReturns);

            Double result = calculator.calculateTotalReturn(PORTFOLIO_ID);

            assertThat(result).isCloseTo(10.0, within(0.01));
        }

        @Test
        void multipleSnapshots_shouldCompoundLastDayOfEachSnapshot() {
            List<PortfolioDailyReturn> dailyReturns = List.of(
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 11), new BigDecimal("3.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 12), new BigDecimal("7.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 13), new BigDecimal("10.0")),
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 14), new BigDecimal("2.0")),
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 15), new BigDecimal("5.0"))
            );

            when(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(dailyReturns);

            Double result = calculator.calculateTotalReturn(PORTFOLIO_ID);

            assertThat(result).isCloseTo(15.5, within(0.01));
        }
    }

    @Nested
    @DisplayName("calculatePortfolioValueSeries")
    class CalculatePortfolioValueSeriesTest {

        @Test
        void multipleSnapshots_shouldKeepChartContinuous() {
            List<PortfolioDailyReturn> dailyReturns = List.of(
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 11), new BigDecimal("3.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 12), new BigDecimal("7.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 13), new BigDecimal("10.0")),
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 14), new BigDecimal("2.0")),
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 15), new BigDecimal("5.0"))
            );

            List<PortfolioReturnCalculator.PortfolioValuePoint> result =
                    calculator.calculatePortfolioValueSeries(dailyReturns, 100.0);

            assertThat(result).hasSize(5);
            assertThat(result.get(0).value()).isCloseTo(103.0, within(0.01));
            assertThat(result.get(1).value()).isCloseTo(107.0, within(0.01));
            assertThat(result.get(2).value()).isCloseTo(110.0, within(0.01));
            assertThat(result.get(3).value()).isCloseTo(112.2, within(0.01));
            assertThat(result.get(4).value()).isCloseTo(115.5, within(0.01));
        }
    }

    @Nested
    @DisplayName("calculateAssetReturns")
    class CalculateAssetReturnsTest {

        @Test
        void singleSnapshot_shouldUseLatestReturnPerAsset() {
            List<SnapshotAssetDailyReturn> assetReturns = List.of(
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 11), new BigDecimal("5.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 12), new BigDecimal("10.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 13), new BigDecimal("15.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 11), new BigDecimal("-2.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 12), new BigDecimal("-3.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 13), new BigDecimal("-5.0"))
            );

            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(assetReturns);

            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1, ASSET_2));

            assertThat(result.get(ASSET_1)).isCloseTo(15.0, within(0.01));
            assertThat(result.get(ASSET_2)).isCloseTo(-5.0, within(0.01));
        }

        @Test
        void rebalance_shouldResetAssetReturnToLatestSnapshot() {
            List<SnapshotAssetDailyReturn> assetReturns = List.of(
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 11), new BigDecimal("5.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 12), new BigDecimal("10.0")),
                    createAssetDailyReturn(SNAPSHOT_B, ASSET_1, LocalDate.of(2026, 3, 14), new BigDecimal("3.0")),
                    createAssetDailyReturn(SNAPSHOT_B, ASSET_1, LocalDate.of(2026, 3, 15), new BigDecimal("5.0"))
            );

            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(assetReturns);

            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1));

            assertThat(result.get(ASSET_1)).isCloseTo(5.0, within(0.01));
        }

        @Test
        void noDataForAsset_shouldReturnZero() {
            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(List.of());

            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1));

            assertThat(result.get(ASSET_1)).isEqualTo(0.0);
        }
    }

    private PortfolioDailyReturn createPortfolioDailyReturn(UUID snapshotId, LocalDate date, BigDecimal returnTotal) {
        return PortfolioDailyReturn.from(
                PORTFOLIO_ID,
                snapshotId,
                date,
                returnTotal,
                returnTotal,
                BigDecimal.ZERO,
                new BigDecimal("10000000")
        );
    }

    private SnapshotAssetDailyReturn createAssetDailyReturn(UUID snapshotId, UUID assetId, LocalDate date, BigDecimal returnTotal) {
        return SnapshotAssetDailyReturn.from(
                PORTFOLIO_ID,
                snapshotId,
                assetId,
                date,
                new BigDecimal("10.0"),
                returnTotal,
                returnTotal,
                BigDecimal.ZERO,
                new BigDecimal("1.0"),
                new BigDecimal("1000000")
        );
    }
}
