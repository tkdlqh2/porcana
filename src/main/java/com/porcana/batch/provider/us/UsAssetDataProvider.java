package com.porcana.batch.provider.us;

import com.porcana.batch.provider.AssetDataProvider;

/**
 * Interface for US market asset data providers
 * Abstracts the source of US market data (FMP, Yahoo Finance, etc.)
 */
public interface UsAssetDataProvider extends AssetDataProvider {

    @Override
    default String getProviderName() {
        return "US_ASSET_PROVIDER";
    }
}