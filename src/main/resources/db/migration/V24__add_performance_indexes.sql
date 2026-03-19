-- Performance optimization indexes
-- Based on performance analysis identifying N+1 queries and missing indexes

-- CRITICAL: Optimize portfolio snapshot lookups with range queries
-- Used by: findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc
CREATE INDEX IF NOT EXISTS idx_portfolio_snapshot_portfolio_date_desc
ON portfolio_snapshots(portfolio_id, effective_date DESC);

-- CRITICAL: Optimize latest asset return lookups
-- Used by: findFirstByPortfolioIdAndAssetIdOrderByReturnDateDesc
CREATE INDEX IF NOT EXISTS idx_snapshot_asset_return_portfolio_asset_date_desc
ON snapshot_asset_daily_returns(portfolio_id, asset_id, return_date DESC);

-- HIGH: Optimize latest price lookups (DESC order)
-- Used by: findFirstByAssetOrderByPriceDateDesc in HoldingBaselineService
CREATE INDEX IF NOT EXISTS idx_asset_price_asset_date_desc
ON asset_prices(asset_id, price_date DESC);

-- HIGH: Optimize guest portfolio queries
-- Used by: findByGuestSessionIdAndDeletedAtIsNull, countByGuestSessionIdAndDeletedAtIsNull
CREATE INDEX IF NOT EXISTS idx_portfolios_guest_session_id
ON portfolios(guest_session_id) WHERE guest_session_id IS NOT NULL;

-- MEDIUM: Optimize soft-delete portfolio queries for users
-- Used by: findByUserIdAndDeletedAtIsNull
CREATE INDEX IF NOT EXISTS idx_portfolios_user_deleted
ON portfolios(user_id, deleted_at) WHERE deleted_at IS NULL;

-- MEDIUM: Optimize arena session portfolio lookups
-- Used by: findByPortfolioId
CREATE INDEX IF NOT EXISTS idx_arena_sessions_portfolio_id
ON arena_sessions(portfolio_id);

-- Comments for documentation
COMMENT ON INDEX idx_portfolio_snapshot_portfolio_date_desc IS
'Optimizes range queries on effective_date with DESC ordering for snapshot lookups';

COMMENT ON INDEX idx_snapshot_asset_return_portfolio_asset_date_desc IS
'Optimizes latest weight lookups per portfolio/asset combination';

COMMENT ON INDEX idx_asset_price_asset_date_desc IS
'Optimizes latest price queries used extensively in HoldingBaselineService';

COMMENT ON INDEX idx_portfolios_guest_session_id IS
'Partial index for guest session lookups (excludes NULL values)';

COMMENT ON INDEX idx_portfolios_user_deleted IS
'Partial index for active (non-deleted) portfolio queries per user';