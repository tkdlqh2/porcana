package com.porcana.batch.job;

import com.porcana.batch.listener.BatchNotificationListener;
import com.porcana.batch.support.BatchIssueCollector;
import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioSnapshotRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPerformanceBatchJobTest {

    @Mock private JobRepository jobRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioSnapshotRepository snapshotRepository;
    @Mock private PortfolioSnapshotAssetRepository snapshotAssetRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetPriceRepository assetPriceRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;
    @Mock private PortfolioDailyReturnRepository dailyReturnRepository;
    @Mock private SnapshotAssetDailyReturnRepository assetDailyReturnRepository;
    @Mock private BatchNotificationListener batchNotificationListener;
    @Mock private BatchIssueCollector batchIssueCollector;

    @Test
    @DisplayName("첫 수익률 계산은 스냅샷 당일이 아니라 직전 가격을 시작가로 사용한다")
    void calculateAssetReturn_usesPreviousPriceWhenSnapshotStartsSameDay() {
        PortfolioPerformanceBatchJob job = new PortfolioPerformanceBatchJob(
                jobRepository,
                transactionManager,
                portfolioRepository,
                snapshotRepository,
                snapshotAssetRepository,
                assetRepository,
                assetPriceRepository,
                exchangeRateRepository,
                dailyReturnRepository,
                assetDailyReturnRepository,
                batchNotificationListener,
                batchIssueCollector
        );

        Asset asset = Asset.builder()
                .market(Asset.Market.KR)
                .symbol("005930")
                .name("Samsung Electronics")
                .type(Asset.AssetType.STOCK)
                .active(true)
                .asOf(LocalDate.of(2026, 4, 29))
                .build();

        AssetPrice previousPrice = AssetPrice.builder()
                .asset(asset)
                .priceDate(LocalDate.of(2026, 4, 28))
                .openPrice(new BigDecimal("100"))
                .highPrice(new BigDecimal("100"))
                .lowPrice(new BigDecimal("100"))
                .closePrice(new BigDecimal("100"))
                .volume(1L)
                .build();

        AssetPrice targetPrice = AssetPrice.builder()
                .asset(asset)
                .priceDate(LocalDate.of(2026, 4, 29))
                .openPrice(new BigDecimal("110"))
                .highPrice(new BigDecimal("110"))
                .lowPrice(new BigDecimal("110"))
                .closePrice(new BigDecimal("110"))
                .volume(1L)
                .build();

        when(assetPriceRepository.findByAssetAndPriceDate(asset, LocalDate.of(2026, 4, 29)))
                .thenReturn(Optional.of(targetPrice));
        when(assetPriceRepository.findByAssetAndPriceDateBetweenOrderByPriceDateAsc(
                asset,
                LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 4, 28)
        )).thenReturn(List.of(previousPrice));

        Optional<?> result = ReflectionTestUtils.invokeMethod(
                job,
                "calculateAssetReturn",
                asset,
                LocalDate.of(2026, 4, 29),
                LocalDate.of(2026, 4, 29),
                1L
        );

        assertThat(result).isPresent();
        Object assetReturn = result.orElseThrow();
        BigDecimal totalReturn = (BigDecimal) ReflectionTestUtils.invokeMethod(assetReturn, "assetReturnTotal");
        assertThat(totalReturn).isEqualByComparingTo("10.000000");
    }

    @Test
    @DisplayName("미국 자산 첫 수익률 계산은 환율도 직전 값을 시작점으로 사용한다")
    void calculateAssetReturn_usesPreviousFxRateWhenSnapshotStartsSameDay() {
        PortfolioPerformanceBatchJob job = new PortfolioPerformanceBatchJob(
                jobRepository,
                transactionManager,
                portfolioRepository,
                snapshotRepository,
                snapshotAssetRepository,
                assetRepository,
                assetPriceRepository,
                exchangeRateRepository,
                dailyReturnRepository,
                assetDailyReturnRepository,
                batchNotificationListener,
                batchIssueCollector
        );

        Asset asset = Asset.builder()
                .market(Asset.Market.US)
                .symbol("AAPL")
                .name("Apple")
                .type(Asset.AssetType.STOCK)
                .active(true)
                .asOf(LocalDate.of(2026, 4, 29))
                .build();

        AssetPrice previousPrice = AssetPrice.builder()
                .asset(asset)
                .priceDate(LocalDate.of(2026, 4, 28))
                .openPrice(new BigDecimal("100"))
                .highPrice(new BigDecimal("100"))
                .lowPrice(new BigDecimal("100"))
                .closePrice(new BigDecimal("100"))
                .volume(1L)
                .build();

        AssetPrice targetPrice = AssetPrice.builder()
                .asset(asset)
                .priceDate(LocalDate.of(2026, 4, 29))
                .openPrice(new BigDecimal("110"))
                .highPrice(new BigDecimal("110"))
                .lowPrice(new BigDecimal("110"))
                .closePrice(new BigDecimal("110"))
                .volume(1L)
                .build();

        ExchangeRate previousRate = ExchangeRate.builder()
                .currencyCode(CurrencyCode.USD)
                .currencyName("US Dollar")
                .baseRate(new BigDecimal("1300"))
                .exchangeDate(LocalDate.of(2026, 4, 28))
                .build();

        ExchangeRate targetRate = ExchangeRate.builder()
                .currencyCode(CurrencyCode.USD)
                .currencyName("US Dollar")
                .baseRate(new BigDecimal("1326"))
                .exchangeDate(LocalDate.of(2026, 4, 29))
                .build();

        when(assetPriceRepository.findByAssetAndPriceDate(asset, LocalDate.of(2026, 4, 29)))
                .thenReturn(Optional.of(targetPrice));
        when(assetPriceRepository.findByAssetAndPriceDateBetweenOrderByPriceDateAsc(
                asset,
                LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 4, 28)
        )).thenReturn(List.of(previousPrice));
        when(exchangeRateRepository.findByCurrencyCodeAndExchangeDate(CurrencyCode.USD, LocalDate.of(2026, 4, 29)))
                .thenReturn(Optional.of(targetRate));
        when(exchangeRateRepository.findByCurrencyCodeAndExchangeDateBetweenOrderByExchangeDateDesc(
                CurrencyCode.USD,
                LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 4, 28)
        )).thenReturn(List.of(previousRate));

        Optional<?> result = ReflectionTestUtils.invokeMethod(
                job,
                "calculateAssetReturn",
                asset,
                LocalDate.of(2026, 4, 29),
                LocalDate.of(2026, 4, 29),
                1L
        );

        assertThat(result).isPresent();
        Object assetReturn = result.orElseThrow();
        BigDecimal fxReturn = (BigDecimal) ReflectionTestUtils.invokeMethod(assetReturn, "fxReturn");
        BigDecimal totalReturn = (BigDecimal) ReflectionTestUtils.invokeMethod(assetReturn, "assetReturnTotal");
        assertThat(fxReturn).isEqualByComparingTo("2.000000");
        assertThat(totalReturn).isEqualByComparingTo("12.000000");
    }
}
