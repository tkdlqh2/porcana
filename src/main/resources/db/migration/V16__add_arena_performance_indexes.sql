-- Add indexes for arena recommendation query performance

-- Index for sector-based filtering with active flag
-- Used in: findBySectorInAndActiveTrue, findBySectorNotInAndActiveTrue
CREATE INDEX IF NOT EXISTS idx_asset_sector_active ON assets(sector, active);

-- Index for PK range random sampling with active flag
-- Used in: bucket sampling queries (findPreferredSectorBucket, etc.)
CREATE INDEX IF NOT EXISTS idx_asset_active_id ON assets(active, id);

-- Index for risk level filtering
-- Used in: risk-based recommendations and statistics
CREATE INDEX IF NOT EXISTS idx_asset_risk_level ON assets(current_risk_level);

-- Add comments for documentation
COMMENT ON INDEX idx_asset_sector_active IS 'Composite index for sector-based active asset queries (arena recommendations)';
COMMENT ON INDEX idx_asset_active_id IS 'Composite index for PK range random sampling (arena bucket sampling)';
COMMENT ON INDEX idx_asset_risk_level IS 'Index for risk level filtering and statistics';