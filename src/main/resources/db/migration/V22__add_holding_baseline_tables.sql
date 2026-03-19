-- Holding Baseline: 사용자가 실제 보유하고 있는 수량 기반 스냅샷

CREATE TABLE portfolio_holding_baselines (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    user_id UUID NOT NULL,
    source_type VARCHAR(20) NOT NULL,  -- MANUAL, SEEDED, BROKER_SYNC
    base_currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    cash_amount DECIMAL(18,2) DEFAULT 0,
    memo VARCHAR(255),
    confirmed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_phb_portfolio FOREIGN KEY (portfolio_id)
        REFERENCES portfolios(id) ON DELETE CASCADE,
    CONSTRAINT fk_phb_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE portfolio_holding_baseline_items (
    id UUID PRIMARY KEY,
    baseline_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    quantity DECIMAL(18,6) NOT NULL,
    avg_price DECIMAL(18,4),
    target_weight_pct DECIMAL(5,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_phbi_baseline FOREIGN KEY (baseline_id)
        REFERENCES portfolio_holding_baselines(id) ON DELETE CASCADE,
    CONSTRAINT fk_phbi_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id)
);

-- 포트폴리오당 baseline은 1개만
CREATE UNIQUE INDEX idx_phb_portfolio ON portfolio_holding_baselines(portfolio_id);

-- baseline item 조회용
CREATE INDEX idx_phbi_baseline ON portfolio_holding_baseline_items(baseline_id);

COMMENT ON TABLE portfolio_holding_baselines IS '포트폴리오의 실제 보유 현황 (Holding Baseline)';
COMMENT ON COLUMN portfolio_holding_baselines.source_type IS 'MANUAL: 직접 입력, SEEDED: 시드 기반 계산, BROKER_SYNC: 증권사 연동';
COMMENT ON COLUMN portfolio_holding_baselines.base_currency IS '기준 통화 (KRW, USD)';
COMMENT ON COLUMN portfolio_holding_baselines.cash_amount IS '현금 보유액';
COMMENT ON COLUMN portfolio_holding_baselines.confirmed_at IS '사용자가 확정한 시점';

COMMENT ON TABLE portfolio_holding_baseline_items IS 'Holding Baseline의 개별 종목 보유 수량';
COMMENT ON COLUMN portfolio_holding_baseline_items.quantity IS '보유 수량';
COMMENT ON COLUMN portfolio_holding_baseline_items.avg_price IS '평균 매수가 (선택)';
COMMENT ON COLUMN portfolio_holding_baseline_items.target_weight_pct IS 'baseline 생성 당시 목표 비중 (참고용)';
