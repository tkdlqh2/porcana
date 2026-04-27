package com.porcana.batch.service;

import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetClass;
import com.porcana.domain.asset.entity.DividendCategory;
import com.porcana.domain.asset.entity.DividendFrequency;
import com.porcana.domain.asset.entity.Sector;
import com.porcana.domain.asset.entity.UniverseTag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KrAssetDescriptionGeneratorTest {

    private final KrAssetDescriptionGenerator generator = new KrAssetDescriptionGenerator();

    @Test
    void generatesStockDescriptionWithSectorUniverseAndDividendHints() {
        Asset asset = Asset.builder()
                .market(Asset.Market.KR)
                .symbol("005930")
                .name("삼성전자")
                .type(Asset.AssetType.STOCK)
                .sector(Sector.INFORMATION_TECHNOLOGY)
                .universeTags(List.of(UniverseTag.KOSPI200))
                .active(true)
                .dividendAvailable(true)
                .dividendCategory(DividendCategory.DIVIDEND_GROWTH)
                .dividendFrequency(DividendFrequency.ANNUAL)
                .asOf(LocalDate.now())
                .build();

        String description = generator.generate(asset);

        assertThat(description).contains("삼성전자(005930)");
        assertThat(description).contains("정보기술");
        assertThat(description).contains("KOSPI 200");
        assertThat(description).contains("배당 성장");
    }

    @Test
    void generatesEtfDescriptionWithAssetClassHint() {
        Asset asset = Asset.builder()
                .market(Asset.Market.KR)
                .symbol("069500")
                .name("KODEX 200")
                .type(Asset.AssetType.ETF)
                .assetClass(AssetClass.EQUITY_INDEX)
                .active(true)
                .dividendAvailable(false)
                .dividendCategory(DividendCategory.NONE)
                .dividendFrequency(DividendFrequency.NONE)
                .asOf(LocalDate.now())
                .build();

        String description = generator.generate(asset);

        assertThat(description).contains("ETF");
        assertThat(description).contains("대표 지수");
        assertThat(description).contains("포트폴리오");
    }
}
