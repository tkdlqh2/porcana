CREATE TABLE IF NOT EXISTS arena_session_markets (
    session_id UUID NOT NULL,
    market VARCHAR(10) NOT NULL CHECK (market IN ('KR', 'US')),
    CONSTRAINT fk_arena_session_markets_session FOREIGN KEY (session_id)
        REFERENCES arena_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS arena_session_asset_types (
    session_id UUID NOT NULL,
    asset_type VARCHAR(10) NOT NULL CHECK (asset_type IN ('STOCK', 'ETF')),
    CONSTRAINT fk_arena_session_asset_types_session FOREIGN KEY (session_id)
        REFERENCES arena_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_arena_session_markets_session_id
    ON arena_session_markets(session_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_arena_session_markets_session_market
    ON arena_session_markets(session_id, market);

CREATE INDEX IF NOT EXISTS idx_arena_session_asset_types_session_id
    ON arena_session_asset_types(session_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_arena_session_asset_types_session_type
    ON arena_session_asset_types(session_id, asset_type);
