-- Add current_risk_level column to assets table
ALTER TABLE assets ADD COLUMN current_risk_level INT NULL;
COMMENT ON COLUMN assets.current_risk_level IS '현재 위험도 (1~5)';

-- Create asset_risk_history table
CREATE TABLE asset_risk_history (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    week VARCHAR(8) NOT NULL,
    risk_level INT NOT NULL,
    risk_score DECIMAL(5,2) NOT NULL,
    volatility DECIMAL(10,6) NOT NULL,
    max_drawdown DECIMAL(10,6) NOT NULL,
    worst_day_return DECIMAL(10,6) NOT NULL,
    factors_snapshot TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_asset_risk_history_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT uk_asset_risk_history_asset_week UNIQUE (asset_id, week)
);

-- Add comments for asset_risk_history columns
COMMENT ON COLUMN asset_risk_history.week IS '주차 (YYYY-WW 포맷)';
COMMENT ON COLUMN asset_risk_history.risk_level IS '위험도 레벨 (1~5)';
COMMENT ON COLUMN asset_risk_history.risk_score IS '위험도 점수 (0~100)';
COMMENT ON COLUMN asset_risk_history.volatility IS '변동성 (연율화)';
COMMENT ON COLUMN asset_risk_history.max_drawdown IS '최대낙폭 (MDD)';
COMMENT ON COLUMN asset_risk_history.worst_day_return IS '1일 최악 하락률';
COMMENT ON COLUMN asset_risk_history.factors_snapshot IS '계산에 사용된 추가 요소 스냅샷 (JSON)';

-- Create indexes for asset_risk_history
CREATE INDEX idx_asset_risk_history_asset_id ON asset_risk_history(asset_id);
CREATE INDEX idx_asset_risk_history_week ON asset_risk_history(week);