package com.porcana.batch.provider.kr;

import com.porcana.batch.provider.AssetDataProvider;

/**
 * Interface for Korean market asset data providers
 * Abstracts the source of Korean market data (data.go.kr, etc.)
 */
public interface KrAssetDataProvider extends AssetDataProvider {

    @Override
    default String getProviderName() {
        return "KR_ASSET_PROVIDER";
    }
}