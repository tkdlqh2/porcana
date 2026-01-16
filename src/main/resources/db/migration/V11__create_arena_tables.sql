-- Arena (투기장) tables for portfolio drafting

-- Arena sessions table (아레나 세션)
CREATE TABLE arena_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    current_round INTEGER NOT NULL CHECK (current_round >= 1 AND current_round <= 12),
    total_rounds INTEGER NOT NULL DEFAULT 12,
    risk_profile VARCHAR(20) CHECK (risk_profile IN ('AGGRESSIVE', 'BALANCED', 'SAFE')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Arena session selected sectors (ElementCollection)
CREATE TABLE arena_session_sectors (
    session_id UUID NOT NULL,
    sector VARCHAR(50) NOT NULL CHECK (sector IN (
        'MATERIALS',
        'COMMUNICATION_SERVICES',
        'CONSUMER_DISCRETIONARY',
        'CONSUMER_STAPLES',
        'ENERGY',
        'FINANCIALS',
        'HEALTH_CARE',
        'INDUSTRIALS',
        'REAL_ESTATE',
        'INFORMATION_TECHNOLOGY',
        'UTILITIES'
    )),
    CONSTRAINT fk_arena_session_sectors_session FOREIGN KEY (session_id)
        REFERENCES arena_sessions(id) ON DELETE CASCADE
);

-- Arena rounds table (각 라운드 정보)
CREATE TABLE arena_rounds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    round_number INTEGER NOT NULL CHECK (round_number >= 1 AND round_number <= 12),
    round_type VARCHAR(20) NOT NULL CHECK (round_type IN ('RISK_PROFILE', 'SECTOR', 'ASSET')),
    selected_asset_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    picked_at TIMESTAMP,
    CONSTRAINT fk_arena_rounds_session FOREIGN KEY (session_id)
        REFERENCES arena_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_arena_rounds_asset FOREIGN KEY (selected_asset_id)
        REFERENCES assets(id) ON DELETE SET NULL
);

-- Arena round choices (라운드별 제시된 선택지 - 3개 종목)
CREATE TABLE arena_round_choices (
    round_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    CONSTRAINT fk_arena_round_choices_round FOREIGN KEY (round_id)
        REFERENCES arena_rounds(id) ON DELETE CASCADE,
    CONSTRAINT fk_arena_round_choices_asset FOREIGN KEY (asset_id)
        REFERENCES assets(id) ON DELETE CASCADE
);

-- Indexes for arena_sessions
CREATE INDEX idx_arena_sessions_portfolio_id ON arena_sessions(portfolio_id);
CREATE INDEX idx_arena_sessions_user_id ON arena_sessions(user_id);
CREATE INDEX idx_arena_sessions_status ON arena_sessions(status);

-- Unique index to prevent duplicate rounds per session
CREATE UNIQUE INDEX idx_arena_round_session_round ON arena_rounds(session_id, round_number);

-- Indexes for arena_round_choices
CREATE INDEX idx_arena_round_choices_round_id ON arena_round_choices(round_id);
CREATE INDEX idx_arena_round_choices_asset_id ON arena_round_choices(asset_id);
CREATE UNIQUE INDEX idx_arena_round_choices_round_asset
    ON arena_round_choices(round_id, asset_id);

-- Index for arena_session_sectors
CREATE INDEX idx_arena_session_sectors_session_id ON arena_session_sectors(session_id);
CREATE UNIQUE INDEX idx_arena_session_sectors_session_sector
    ON arena_session_sectors(session_id, sector);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_arena_sessions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_arena_sessions_updated_at
    BEFORE UPDATE ON arena_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_arena_sessions_updated_at();
