package com.porcana.domain.portfolio.service;

import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import org.junit.jupiter.api.BeforeEach;
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
        @DisplayName("단일 스냅샷 내 여러 일자 - 마지막 날의 누적 수익률만 사용해야 함")
        void singleSnapshot_shouldUseOnlyLastDayReturn() {
            // Given: 스냅샷 A에 3일치 누적 수익률 데이터
            // DB에는 매일의 "스냅샷 시작 대비 누적 수익률"이 저장됨
            List<PortfolioDailyReturn> dailyReturns = List.of(
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 11), new BigDecimal("2.0")),  // Day 1: 2%
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 12), new BigDecimal("5.0")),  // Day 2: 5%
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 13), new BigDecimal("10.0")) // Day 3: 10%
            );

            when(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(dailyReturns);

            // When
            Double result = calculator.calculateTotalReturn(PORTFOLIO_ID);

            // Then: 마지막 날의 누적 수익률 10%만 사용해야 함
            // 잘못된 계산: (1.02) × (1.05) × (1.10) - 1 = 17.8% (버그)
            // 올바른 계산: 10% (마지막 날 누적 수익률)
            assertThat(result).isCloseTo(10.0, within(0.01));
        }

        @Test
        @DisplayName("여러 스냅샷 - 각 스냅샷의 마지막 수익률을 복리로 연결")
        void multipleSnapshots_shouldCompoundLastDayOfEachSnapshot() {
            // Given: 스냅샷 A (10% 수익) → 리밸런싱 → 스냅샷 B (5% 수익)
            List<PortfolioDailyReturn> dailyReturns = List.of(
                    // Snapshot A
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 11), new BigDecimal("3.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 12), new BigDecimal("7.0")),
                    createPortfolioDailyReturn(SNAPSHOT_A, LocalDate.of(2026, 3, 13), new BigDecimal("10.0")), // 마지막: 10%
                    // Snapshot B (리밸런싱 후 새 기간)
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 14), new BigDecimal("2.0")),
                    createPortfolioDailyReturn(SNAPSHOT_B, LocalDate.of(2026, 3, 15), new BigDecimal("5.0"))   // 마지막: 5%
            );

            when(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(dailyReturns);

            // When
            Double result = calculator.calculateTotalReturn(PORTFOLIO_ID);

            // Then: (1.10) × (1.05) - 1 = 15.5%
            assertThat(result).isCloseTo(15.5, within(0.01));
        }

        @Test
        @DisplayName("빈 데이터 - 0% 반환")
        void emptyData_shouldReturnZero() {
            when(portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(List.of());

            Double result = calculator.calculateTotalReturn(PORTFOLIO_ID);

            assertThat(result).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("calculateAssetReturns")
    class CalculateAssetReturnsTest {

        @Test
        @DisplayName("단일 스냅샷 내 여러 일자 - 자산별로 마지막 날 수익률만 사용")
        void singleSnapshot_shouldUseOnlyLastDayReturnPerAsset() {
            // Given: 두 자산의 3일치 누적 수익률 데이터
            List<SnapshotAssetDailyReturn> assetReturns = List.of(
                    // Asset 1: 매일 누적 수익률 증가
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 11), new BigDecimal("5.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 12), new BigDecimal("10.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 13), new BigDecimal("15.0")), // 마지막: 15%
                    // Asset 2: 하락
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 11), new BigDecimal("-2.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 12), new BigDecimal("-3.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_2, LocalDate.of(2026, 3, 13), new BigDecimal("-5.0"))  // 마지막: -5%
            );

            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(assetReturns);

            // When
            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1, ASSET_2));

            // Then
            assertThat(result.get(ASSET_1)).isCloseTo(15.0, within(0.01));
            assertThat(result.get(ASSET_2)).isCloseTo(-5.0, within(0.01));
        }

        @Test
        @DisplayName("여러 스냅샷 - 자산별로 각 스냅샷의 마지막 수익률을 복리로 연결")
        void multipleSnapshots_shouldCompoundLastDayOfEachSnapshotPerAsset() {
            // Given: 스냅샷 A → 리밸런싱 → 스냅샷 B
            List<SnapshotAssetDailyReturn> assetReturns = List.of(
                    // Snapshot A - Asset 1
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 11), new BigDecimal("5.0")),
                    createAssetDailyReturn(SNAPSHOT_A, ASSET_1, LocalDate.of(2026, 3, 12), new BigDecimal("10.0")), // 마지막: 10%
                    // Snapshot B - Asset 1 (리밸런싱 후)
                    createAssetDailyReturn(SNAPSHOT_B, ASSET_1, LocalDate.of(2026, 3, 14), new BigDecimal("3.0")),
                    createAssetDailyReturn(SNAPSHOT_B, ASSET_1, LocalDate.of(2026, 3, 15), new BigDecimal("5.0"))   // 마지막: 5%
            );

            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(assetReturns);

            // When
            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1));

            // Then: (1.10) × (1.05) - 1 = 15.5%
            assertThat(result.get(ASSET_1)).isCloseTo(15.5, within(0.01));
        }

        @Test
        @DisplayName("자산 데이터 없음 - 해당 자산은 0% 반환")
        void noDataForAsset_shouldReturnZero() {
            when(snapshotAssetDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(PORTFOLIO_ID))
                    .thenReturn(List.of());

            Map<UUID, Double> result = calculator.calculateAssetReturns(PORTFOLIO_ID, Set.of(ASSET_1));

            assertThat(result.get(ASSET_1)).isEqualTo(0.0);
        }
    }

    // Helper methods
    private PortfolioDailyReturn createPortfolioDailyReturn(UUID snapshotId, LocalDate date, BigDecimal returnTotal) {
        return PortfolioDailyReturn.from(
                PORTFOLIO_ID,
                snapshotId,
                date,
                returnTotal,
                returnTotal, // returnLocal = returnTotal for simplicity
                BigDecimal.ZERO, // returnFx
                new BigDecimal("10000000") // totalValueKrw
        );
    }

    private SnapshotAssetDailyReturn createAssetDailyReturn(UUID snapshotId, UUID assetId, LocalDate date, BigDecimal returnTotal) {
        return SnapshotAssetDailyReturn.from(
                PORTFOLIO_ID,
                snapshotId,
                assetId,
                date,
                new BigDecimal("10.0"), // weightUsed
                returnTotal, // assetReturnLocal
                returnTotal, // assetReturnTotal
                BigDecimal.ZERO, // fxReturn
                new BigDecimal("1.0"), // contributionTotal
                new BigDecimal("1000000") // valueKrw
        );
    }
}
