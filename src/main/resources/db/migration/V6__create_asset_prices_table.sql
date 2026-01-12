-- Create asset_prices table for EOD (End-of-Day) price data
CREATE TABLE asset_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    price_date DATE NOT NULL,
    price DECIMAL(20, 4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_asset_price_asset_date UNIQUE (asset_id, price_date)
);

-- Create indexes for efficient querying
CREATE INDEX idx_asset_price_asset_date ON asset_prices(asset_id, price_date);
CREATE INDEX idx_asset_price_date ON asset_prices(price_date);
CREATE INDEX idx_asset_price_asset ON asset_prices(asset_id);

-- Add comment
COMMENT ON TABLE asset_prices IS 'End-of-Day price data for assets (price and volume only)';
COMMENT ON COLUMN asset_prices.price_date IS 'Date of the price data';
COMMENT ON COLUMN asset_prices.price IS 'Closing price';
COMMENT ON COLUMN asset_prices.volume IS 'Trading volume';