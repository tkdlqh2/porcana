-- Asset management tables

-- Assets table (종목 테이블)
CREATE TABLE assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market VARCHAR(10) NOT NULL CHECK (market IN ('KR', 'US')),
    symbol VARCHAR(20) NOT NULL,
    exchange VARCHAR(50),
    name VARCHAR(200) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('STOCK', 'ETF')),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    as_of DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asset universe tags (ElementCollection)
CREATE TABLE asset_universe_tags (
    asset_id UUID NOT NULL,
    tag VARCHAR(50) NOT NULL CHECK (tag IN (
        'SP500', 'NASDAQ100', 'DOW30',
        'KOSPI200', 'KOSDAQ150',
        'ETF_CORE', 'ETF_SECTOR', 'ETF_THEMATIC',
        'MEGA_CAP', 'LARGE_CAP', 'MID_CAP', 'GROWTH', 'VALUE'
    )),
    CONSTRAINT fk_asset_universe_tags_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id) ON DELETE CASCADE
);

-- Indexes for assets table
CREATE UNIQUE INDEX idx_asset_symbol_market ON assets(symbol, market);
CREATE INDEX idx_asset_active ON assets(active);
CREATE INDEX idx_asset_market ON assets(market);

-- Index for universe tags
CREATE INDEX idx_asset_universe_tags_asset_id ON asset_universe_tags(asset_id);
CREATE INDEX idx_asset_universe_tags_tag ON asset_universe_tags(tag);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_assets_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_assets_updated_at
    BEFORE UPDATE ON assets
    FOR EACH ROW
    EXECUTE FUNCTION update_assets_updated_at();