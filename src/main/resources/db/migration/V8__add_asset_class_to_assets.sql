-- Add asset_class column to assets table for ETF classification
-- asset_class is only applicable to ETF type assets
-- NULL for STOCK type assets

ALTER TABLE assets ADD COLUMN asset_class VARCHAR(50) CHECK (
    asset_class IS NULL OR
    asset_class IN ('EQUITY_INDEX', 'SECTOR', 'DIVIDEND', 'BOND', 'COMMODITY')
);

-- Add index for asset_class to optimize queries filtering by asset class
CREATE INDEX idx_asset_asset_class ON assets(asset_class) WHERE asset_class IS NOT NULL;

-- Add comment to document the column's purpose
COMMENT ON COLUMN assets.asset_class IS 'ETF asset class classification (EQUITY_INDEX, SECTOR, DIVIDEND, BOND, COMMODITY). NULL for STOCK type assets.';
