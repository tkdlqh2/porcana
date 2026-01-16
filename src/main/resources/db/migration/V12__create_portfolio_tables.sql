-- Portfolio tables for managing user portfolios

-- Portfolios table
CREATE TABLE portfolios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'FINISHED')),
    started_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Portfolio assets (holdings)
CREATE TABLE portfolio_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    weight_pct DECIMAL(5, 2) NOT NULL CHECK (weight_pct >= 0 AND weight_pct <= 100),
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portfolio_assets_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_assets_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id) ON DELETE CASCADE
);

-- Indexes for portfolios
CREATE INDEX idx_portfolios_user_id ON portfolios(user_id);
CREATE INDEX idx_portfolios_status ON portfolios(status);

-- Indexes for portfolio_assets
CREATE INDEX idx_portfolio_assets_portfolio_id ON portfolio_assets(portfolio_id);
CREATE INDEX idx_portfolio_assets_asset_id ON portfolio_assets(asset_id);

-- Unique constraint to prevent duplicate assets in same portfolio
CREATE UNIQUE INDEX idx_portfolio_assets_portfolio_asset ON portfolio_assets(portfolio_id, asset_id);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_portfolios_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_portfolios_updated_at
    BEFORE UPDATE ON portfolios
    FOR EACH ROW
    EXECUTE FUNCTION update_portfolios_updated_at();
