-- Add value_krw columns to portfolio daily returns tables
-- These columns track the actual KRW-based portfolio value (assuming 10M KRW initial investment)

-- Add total_value_krw to portfolio_daily_returns
ALTER TABLE portfolio_daily_returns
ADD COLUMN total_value_krw DECIMAL(20, 2) NOT NULL DEFAULT 10000000.00;

COMMENT ON COLUMN portfolio_daily_returns.total_value_krw IS '포트폴리오 전체 평가금액 (원화 기준, 초기 10,000,000원 투자 가정)';

-- Add value_krw to snapshot_asset_daily_returns
ALTER TABLE snapshot_asset_daily_returns
ADD COLUMN value_krw DECIMAL(20, 2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN snapshot_asset_daily_returns.value_krw IS '자산 평가금액 (원화 기준)';

-- Update existing data: Calculate value_krw based on weightUsed
-- value_krw = 10,000,000 * (weightUsed / 100) * (1 + assetReturnTotal / 100)
UPDATE snapshot_asset_daily_returns
SET value_krw = 10000000.00 * (weight_used / 100.0) * (1.0 + asset_return_total / 100.0)
WHERE value_krw = 0.00;

-- Update existing data: Calculate total_value_krw based on return
-- total_value_krw = 10,000,000 * (1 + returnTotal / 100)
UPDATE portfolio_daily_returns
SET total_value_krw = 10000000.00 * (1.0 + return_total / 100.0)
WHERE total_value_krw = 10000000.00;