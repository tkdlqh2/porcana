package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import com.porcana.domain.portfolio.dto.PortfolioDetailResponse;
import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioHoldingBaselineRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingBaselineServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioAssetRepository portfolioAssetRepository;
    @Mock private PortfolioHoldingBaselineRepository baselineRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetPriceRepository assetPriceRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private HoldingBaselineService holdingBaselineService;

    @Test
    @DisplayName("잔금이 남아 있어도 가격 변동이 없으면 baseline profit은 0이다")
    void getBaselineSummaryInternal_doesNotTreatCashAsProfit() {
        UUID portfolioId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        PortfolioHoldingBaseline baseline = PortfolioHoldingBaseline.create(
                portfolioId,
                userId,
                PortfolioHoldingBaseline.SourceType.SEEDED,
                PortfolioHoldingBaseline.Currency.KRW,
                new BigDecimal("1000"),
                "seed"
        );
        baseline.addItem(assetId, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("50"));

        Asset asset = Asset.builder()
                .market(Asset.Market.KR)
                .symbol("005930")
                .name("Samsung Electronics")
                .type(Asset.AssetType.STOCK)
                .active(true)
                .asOf(LocalDate.of(2026, 4, 30))
                .build();
        ReflectionTestUtils.setField(asset, "id", assetId);

        AssetPrice latestPrice = AssetPrice.builder()
                .asset(asset)
                .priceDate(LocalDate.of(2026, 4, 30))
                .openPrice(new BigDecimal("100"))
                .highPrice(new BigDecimal("100"))
                .lowPrice(new BigDecimal("100"))
                .closePrice(new BigDecimal("100"))
                .volume(1L)
                .build();

        ExchangeRate latestRate = ExchangeRate.builder()
                .currencyCode(CurrencyCode.USD)
                .currencyName("US Dollar")
                .baseRate(new BigDecimal("1300"))
                .exchangeDate(LocalDate.of(2026, 4, 30))
                .build();

        when(baselineRepository.findByPortfolioIdWithItems(portfolioId)).thenReturn(Optional.of(baseline));
        when(exchangeRateRepository.findTopByCurrencyCodeOrderByExchangeDateDesc(CurrencyCode.USD))
                .thenReturn(Optional.of(latestRate));
        when(assetRepository.findAllById(List.of(assetId))).thenReturn(List.of(asset));
        when(assetPriceRepository.findLatestPricesByAssetIds(List.of(assetId))).thenReturn(List.of(latestPrice));

        PortfolioDetailResponse.BaselineSummary summary =
                holdingBaselineService.getBaselineSummaryInternal(portfolioId);

        assertThat(summary).isNotNull();
        assertThat(summary.getCashAmount()).isEqualByComparingTo("1000");
        assertThat(summary.getSeedMoney()).isEqualByComparingTo("2000");
        assertThat(summary.getTotalValue()).isEqualByComparingTo("2000");
        assertThat(summary.getProfitAmount()).isEqualByComparingTo("0");
        assertThat(summary.getProfitPct()).isEqualTo(0.0);
    }
}
