package com.porcana.batch.provider;

import com.porcana.batch.dto.AssetBatchDto;

import java.util.List;

/**
 * Interface for external asset data providers
 * Implementations can fetch data from various sources (APIs, CSV, etc.)
 *
 * This abstraction allows easy switching between different data providers
 * without changing batch job logic
 */
public interface AssetDataProvider {

    /**
     * Fetch all assets from the external source
     *
     * @return List of asset DTOs
     * @throws AssetDataProviderException if data fetching fails
     */
    List<AssetBatchDto> fetchAssets() throws AssetDataProviderException;

    /**
     * Get the provider name for logging and monitoring
     */
    String getProviderName();

    /**
     * Exception thrown when asset data fetching fails
     */
    class AssetDataProviderException extends Exception {
        public AssetDataProviderException(String message, Throwable cause) {
            super(message, cause);
        }

        public AssetDataProviderException(String message) {
            super(message);
        }
    }
}