-- Portfolio performance tables for tracking daily returns and snapshots

-- Portfolio snapshots table
CREATE TABLE portfolio_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL,
    effective_date DATE NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portfolio_snapshots_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE
);

-- Portfolio snapshot assets table
CREATE TABLE portfolio_snapshot_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    weight DECIMAL(5, 2) NOT NULL CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT fk_portfolio_snapshot_assets_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES portfolio_snapshots(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_snapshot_assets_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id) ON DELETE CASCADE
);

-- Portfolio daily returns table
CREATE TABLE portfolio_daily_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    return_date DATE NOT NULL,
    return_total DECIMAL(10, 4) NOT NULL,
    return_local DECIMAL(10, 4) NOT NULL,
    return_fx DECIMAL(10, 4) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_portfolio_daily_returns_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_portfolio_daily_returns_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES portfolio_snapshots(id) ON DELETE CASCADE
);

-- Snapshot asset daily returns table
CREATE TABLE snapshot_asset_daily_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    return_date DATE NOT NULL,
    weight_used DECIMAL(5, 2) NOT NULL,
    asset_return_local DECIMAL(10, 4) NOT NULL,
    asset_return_total DECIMAL(10, 4) NOT NULL,
    fx_return DECIMAL(10, 4) NOT NULL,
    contribution_total DECIMAL(10, 4) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshot_asset_daily_returns_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_snapshot_asset_daily_returns_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES portfolio_snapshots(id) ON DELETE CASCADE,
    CONSTRAINT fk_snapshot_asset_daily_returns_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id) ON DELETE CASCADE
);

-- Indexes for portfolio_snapshots
CREATE INDEX idx_portfolio_snapshot_portfolio_id ON portfolio_snapshots(portfolio_id);
CREATE INDEX idx_portfolio_snapshot_effective_date ON portfolio_snapshots(effective_date);
CREATE UNIQUE INDEX idx_portfolio_snapshot_portfolio_date ON portfolio_snapshots(portfolio_id, effective_date);

-- Indexes for portfolio_snapshot_assets
CREATE INDEX idx_portfolio_snapshot_asset_snapshot_id ON portfolio_snapshot_assets(snapshot_id);
CREATE INDEX idx_portfolio_snapshot_asset_asset_id ON portfolio_snapshot_assets(asset_id);
CREATE UNIQUE INDEX idx_portfolio_snapshot_asset_snapshot_asset ON portfolio_snapshot_assets(snapshot_id, asset_id);

-- Indexes for portfolio_daily_returns
CREATE INDEX idx_portfolio_daily_return_portfolio_id ON portfolio_daily_returns(portfolio_id);
CREATE INDEX idx_portfolio_daily_return_snapshot_id ON portfolio_daily_returns(snapshot_id);
CREATE INDEX idx_portfolio_daily_return_date ON portfolio_daily_returns(return_date);
CREATE UNIQUE INDEX idx_portfolio_daily_return_portfolio_date ON portfolio_daily_returns(portfolio_id, return_date);

-- Indexes for snapshot_asset_daily_returns
CREATE INDEX idx_snapshot_asset_daily_return_portfolio_id ON snapshot_asset_daily_returns(portfolio_id);
CREATE INDEX idx_snapshot_asset_daily_return_snapshot_id ON snapshot_asset_daily_returns(snapshot_id);
CREATE INDEX idx_snapshot_asset_daily_return_asset_id ON snapshot_asset_daily_returns(asset_id);
CREATE INDEX idx_snapshot_asset_daily_return_date ON snapshot_asset_daily_returns(return_date);
CREATE UNIQUE INDEX idx_snapshot_asset_daily_return_unique ON snapshot_asset_daily_returns(portfolio_id, snapshot_id, asset_id, return_date);