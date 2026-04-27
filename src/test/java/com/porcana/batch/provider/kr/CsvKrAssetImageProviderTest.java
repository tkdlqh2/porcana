package com.porcana.batch.provider.kr;

import com.porcana.domain.asset.entity.Asset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CsvKrAssetImageProviderTest {

    @Test
    void findsImageFromCatalog() {
        CsvKrAssetImageProvider provider = new CsvKrAssetImageProvider(
                new DefaultResourceLoader(),
                "classpath:batch/test_kr_asset_images.csv"
        );

        Asset asset = Asset.builder()
                .market(Asset.Market.KR)
                .symbol("005930")
                .name("삼성전자")
                .type(Asset.AssetType.STOCK)
                .active(true)
                .asOf(LocalDate.now())
                .build();

        var candidate = provider.findImage(asset);

        assertThat(candidate).isPresent();
        assertThat(candidate.get().imageUrl()).isEqualTo("https://cdn.example.com/images/samsung.png");
        assertThat(candidate.get().source()).isEqualTo("official-ir");
    }

    @Test
    void rejectsPlaceholderStyleUrls() {
        CsvKrAssetImageProvider provider = new CsvKrAssetImageProvider(
                new DefaultResourceLoader(),
                "classpath:batch/test_kr_asset_images.csv"
        );

        assertThat(provider.isValidImageUrl("https://cdn.example.com/defaultImage.png")).isFalse();
        assertThat(provider.isValidImageUrl("https://cdn.example.com/placeholder/logo.png")).isFalse();
        assertThat(provider.isValidImageUrl("https://cdn.example.com/images/real-logo.png")).isTrue();
    }
}
