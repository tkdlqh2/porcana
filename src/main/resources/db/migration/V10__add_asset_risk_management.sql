-- Add current_risk_level column to assets table
ALTER TABLE assets ADD COLUMN current_risk_level INT NULL COMMENT '현재 위험도 (1~5)';

-- Create asset_risk_history table
CREATE TABLE asset_risk_history (
    id BINARY(16) PRIMARY KEY,
    asset_id BINARY(16) NOT NULL,
    week VARCHAR(8) NOT NULL COMMENT '주차 (YYYY-WW 포맷)',
    risk_level INT NOT NULL COMMENT '위험도 레벨 (1~5)',
    risk_score DECIMAL(5,2) NOT NULL COMMENT '위험도 점수 (0~100)',
    volatility DECIMAL(10,6) NOT NULL COMMENT '변동성 (연율화)',
    max_drawdown DECIMAL(10,6) NOT NULL COMMENT '최대낙폭 (MDD)',
    worst_day_return DECIMAL(10,6) NOT NULL COMMENT '1일 최악 하락률',
    factors_snapshot TEXT NULL COMMENT '계산에 사용된 추가 요소 스냅샷 (JSON)',
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_asset_risk_history_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT uk_asset_risk_history_asset_week UNIQUE (asset_id, week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create indexes for asset_risk_history
CREATE INDEX idx_asset_risk_history_asset_id ON asset_risk_history(asset_id);
CREATE INDEX idx_asset_risk_history_week ON asset_risk_history(week);