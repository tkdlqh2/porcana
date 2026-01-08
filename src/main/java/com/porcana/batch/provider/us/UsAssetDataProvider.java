package com.porcana.batch.provider.us;

import com.porcana.batch.provider.AssetDataProvider;

/**
 * Interface for US market asset data providers
 * Abstracts the source of US market data (FMP, Yahoo Finance, etc.)
 */
public interface UsAssetDataProvider extends AssetDataProvider {

    /**
     * Fetch US market assets (typically S&P 500 constituents)
     * Returns data with SP500 universe tag already applied
     */
    @Override
    default String getProviderName() {
        return "US_ASSET_PROVIDER";
    }
}