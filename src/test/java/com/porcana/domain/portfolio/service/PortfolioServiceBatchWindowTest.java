package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.portfolio.command.UpdateAssetWeightsCommand;
import com.porcana.domain.portfolio.repository.*;
import com.porcana.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PortfolioService.validateNotInBatchWindow()
 *
 * 배치 실행 시간대(07:00~07:45 KST, 화-토)에는 비중 수정 불가 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceBatchWindowTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioAssetRepository portfolioAssetRepository;
    @Mock private PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private UserRepository userRepository;
    @Mock private PortfolioReturnCalculator portfolioReturnCalculator;
    @Mock private PortfolioSnapshotService portfolioSnapshotService;
    @Mock private SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;
    @Mock private PortfolioSnapshotRepository portfolioSnapshotRepository;
    @Mock private PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private UpdateAssetWeightsCommand validCommand;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @BeforeEach
    void setUp() {
        validCommand = UpdateAssetWeightsCommand.builder()
                .portfolioId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .userId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .weights(List.of(
                        UpdateAssetWeightsCommand.AssetWeightUpdate.builder()
                                .assetId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                .weightPct(new BigDecimal("100.0"))
                                .build()
                ))
                .build();
    }

    // -----------------------------------------------------------------------
    // 배치 실행 시간대 (화-토, 07:00~07:44:59)에는 비중 수정 불가
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("화요일 07:00 - 배치 윈도우 시작(포함) - 비중 수정 불가")
    void updateAssetWeights_fail_tuesday_batchWindowStart() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 7, 0, 0, 0, KST); // Tuesday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("화요일 07:30 - 배치 윈도우 중간 - 비중 수정 불가")
    void updateAssetWeights_fail_tuesday_batchWindowMiddle() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 7, 30, 0, 0, KST); // Tuesday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("화요일 07:44 - 배치 윈도우 끝 직전 - 비중 수정 불가")
    void updateAssetWeights_fail_tuesday_batchWindowJustBeforeEnd() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 7, 44, 59, 999_999_999, KST); // Tuesday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("수요일 07:15 - 배치 윈도우 내 - 비중 수정 불가")
    void updateAssetWeights_fail_wednesday_batchWindow() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 18, 7, 15, 0, 0, KST); // Wednesday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("토요일 07:00 - 배치 윈도우 (토요일도 배치일) - 비중 수정 불가")
    void updateAssetWeights_fail_saturday_batchWindow() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 21, 7, 0, 0, 0, KST); // Saturday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("토요일 07:44 - 배치 윈도우 끝 직전 - 비중 수정 불가")
    void updateAssetWeights_fail_saturday_batchWindowEnd() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 21, 7, 44, 0, 0, KST); // Saturday
        assertBatchWindowBlocks(mockTime);
    }

    // -----------------------------------------------------------------------
    // 배치 윈도우 외 시간에는 정상 처리 (비중 수정 허용)
    // IllegalStateException 대신 "Portfolio not found" (IllegalArgumentException)이 발생해야 함
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("화요일 06:59 - 배치 윈도우 이전 - 비중 수정 허용")
    void updateAssetWeights_allowed_tuesday_beforeBatchWindow() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 6, 59, 59, 0, KST); // Tuesday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("화요일 07:45 - 배치 윈도우 종료(미포함) - 비중 수정 허용")
    void updateAssetWeights_allowed_tuesday_afterBatchWindow() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 7, 45, 0, 0, KST); // Tuesday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("화요일 08:00 - 배치 윈도우 이후 - 비중 수정 허용")
    void updateAssetWeights_allowed_tuesday_afterBatchWindowLater() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 8, 0, 0, 0, KST); // Tuesday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("월요일 07:30 - 배치 미실행 요일 - 비중 수정 허용")
    void updateAssetWeights_allowed_monday_batchWindowTime() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 16, 7, 30, 0, 0, KST); // Monday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("일요일 07:30 - 배치 미실행 요일 - 비중 수정 허용")
    void updateAssetWeights_allowed_sunday_batchWindowTime() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 15, 7, 30, 0, 0, KST); // Sunday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("일요일 07:00 - 배치 윈도우 시각이지만 미실행 요일 - 비중 수정 허용")
    void updateAssetWeights_allowed_sunday_exactBatchStart() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 15, 7, 0, 0, 0, KST); // Sunday
        assertBatchWindowAllows(mockTime);
    }

    // -----------------------------------------------------------------------
    // 경계값 검증
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("화요일 00:00 - 자정 - 비중 수정 허용")
    void updateAssetWeights_allowed_tuesday_midnight() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 0, 0, 0, 0, KST); // Tuesday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("화요일 23:59 - 자정 직전 - 비중 수정 허용")
    void updateAssetWeights_allowed_tuesday_beforeMidnight() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 23, 59, 59, 0, KST); // Tuesday
        assertBatchWindowAllows(mockTime);
    }

    @Test
    @DisplayName("금요일 07:00 - 배치 윈도우 시작 - 비중 수정 불가")
    void updateAssetWeights_fail_friday_batchWindowStart() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 20, 7, 0, 0, 0, KST); // Friday
        assertBatchWindowBlocks(mockTime);
    }

    @Test
    @DisplayName("예외 메시지에 배치 윈도우 시간 안내 포함 확인")
    void updateAssetWeights_fail_exceptionMessageContainsBatchWindowInfo() {
        ZonedDateTime mockTime = ZonedDateTime.of(2026, 3, 17, 7, 30, 0, 0, KST); // Tuesday

        try (MockedStatic<ZonedDateTime> mockedZonedDateTime = mockStatic(ZonedDateTime.class)) {
            mockedZonedDateTime.when(() -> ZonedDateTime.now(KST)).thenReturn(mockTime);
            mockedZonedDateTime.when(() -> ZonedDateTime.now(any(ZoneId.class))).thenReturn(mockTime);

            assertThatThrownBy(() -> portfolioService.updateAssetWeights(validCommand))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("7:00")
                    .hasMessageContaining("7:45");
        }
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /**
     * 주어진 시각이 배치 윈도우 내에 있을 경우 IllegalStateException이 발생하는지 검증한다.
     */
    private void assertBatchWindowBlocks(ZonedDateTime mockTime) {
        try (MockedStatic<ZonedDateTime> mockedZonedDateTime = mockStatic(ZonedDateTime.class)) {
            mockedZonedDateTime.when(() -> ZonedDateTime.now(KST)).thenReturn(mockTime);
            mockedZonedDateTime.when(() -> ZonedDateTime.now(any(ZoneId.class))).thenReturn(mockTime);

            assertThatThrownBy(() -> portfolioService.updateAssetWeights(validCommand))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("비중 수정은 오전 7:00~7:45 사이에 불가능합니다");
        }
    }

    /**
     * 주어진 시각이 배치 윈도우 외에 있을 경우 배치 윈도우 제한이 없고 (IllegalStateException 미발생),
     * 이후 로직(포트폴리오 조회)이 실행됨을 검증한다.
     */
    private void assertBatchWindowAllows(ZonedDateTime mockTime) {
        when(portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Optional.empty());

        try (MockedStatic<ZonedDateTime> mockedZonedDateTime = mockStatic(ZonedDateTime.class)) {
            mockedZonedDateTime.when(() -> ZonedDateTime.now(KST)).thenReturn(mockTime);
            mockedZonedDateTime.when(() -> ZonedDateTime.now(any(ZoneId.class))).thenReturn(mockTime);

            // 배치 윈도우 제한이 없으므로 다음 로직(포트폴리오 조회)이 실행되어
            // "Portfolio not found" IllegalArgumentException이 발생해야 한다.
            assertThatThrownBy(() -> portfolioService.updateAssetWeights(validCommand))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Portfolio not found");
        }
    }
}