-- Test data for DeletedPortfolioCleanupBatchJobTest

-- Clean up existing test data
DELETE FROM arena_round_choices WHERE round_id IN (
    SELECT id FROM arena_rounds WHERE session_id IN (
        SELECT id FROM arena_sessions WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
    )
);
DELETE FROM arena_rounds WHERE session_id IN (
    SELECT id FROM arena_sessions WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
);
DELETE FROM arena_sessions WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001';
DELETE FROM snapshot_asset_daily_returns WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
);
DELETE FROM portfolio_daily_returns WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
);
DELETE FROM portfolio_snapshot_assets WHERE snapshot_id IN (
    SELECT id FROM portfolio_snapshots WHERE portfolio_id IN (
        SELECT id FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
    )
);
DELETE FROM portfolio_snapshots WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
);
DELETE FROM portfolio_assets WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001'
);
DELETE FROM portfolios WHERE user_id = 'bbbbbbbb-0000-0000-0000-000000000001';
DELETE FROM users WHERE id = 'bbbbbbbb-0000-0000-0000-000000000001';
DELETE FROM assets WHERE symbol IN ('BATCH_TEST_1', 'BATCH_TEST_2');

-- Insert test user
INSERT INTO users (id, email, password, nickname, provider, created_at, updated_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'batch-test@example.com', 'password123', '배치테스터', 'EMAIL', NOW(), NOW());

-- Insert test assets
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, created_at, updated_at, as_of)
VALUES
    ('cccccccc-0000-0000-0000-000000000001', 'BATCH_TEST_1', '배치테스트자산1', 'KR', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, NOW(), NOW(), NOW()),
    ('dddddddd-0000-0000-0000-000000000001', 'BATCH_TEST_2', '배치테스트자산2', 'US', 'STOCK', 'HEALTH_CARE', 4, true, NOW(), NOW(), NOW());

-- Insert old deleted portfolio (31 days ago) - should be hard deleted
INSERT INTO portfolios (id, user_id, name, status, started_at, created_at, updated_at, deleted_at)
VALUES ('eeeeeeee-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', 'Old Deleted Portfolio', 'ACTIVE',
        NOW() - INTERVAL '100 days', NOW() - INTERVAL '100 days', NOW() - INTERVAL '31 days', NOW() - INTERVAL '31 days');

-- Insert portfolio assets for old deleted portfolio
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct, added_at)
VALUES ('ffffffff-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 50.00, NOW() - INTERVAL '100 days');

-- Insert portfolio snapshot for old deleted portfolio
INSERT INTO portfolio_snapshots (id, portfolio_id, effective_date, note, created_at)
VALUES ('11111111-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', NOW() - INTERVAL '50 days', 'Initial', NOW() - INTERVAL '50 days');

-- Insert portfolio snapshot asset
INSERT INTO portfolio_snapshot_assets (id, snapshot_id, asset_id, weight)
VALUES ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001', 50.00);

-- Insert portfolio daily return
INSERT INTO portfolio_daily_returns (id, portfolio_id, snapshot_id, return_date, return_total, return_local, return_fx, total_value_krw, calculated_at)
VALUES ('33333333-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001',
        NOW() - INTERVAL '45 days', 1.50, 1.20, 0.30, 10150000.00, NOW() - INTERVAL '45 days');

-- Insert snapshot asset daily return
INSERT INTO snapshot_asset_daily_returns (id, portfolio_id, snapshot_id, asset_id, return_date, weight_used,
                                         asset_return_local, asset_return_total, fx_return, contribution_total, value_krw, calculated_at)
VALUES ('44444444-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001',
        'cccccccc-0000-0000-0000-000000000001', NOW() - INTERVAL '45 days', 50.00, 1.20, 1.50, 0.30, 0.75, 5075000.00, NOW() - INTERVAL '45 days');

-- Insert arena session for old deleted portfolio
INSERT INTO arena_sessions (id, portfolio_id, user_id, status, current_round, total_rounds, created_at, updated_at, completed_at)
VALUES ('55555555-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001',
        'COMPLETED', 10, 11, NOW() - INTERVAL '100 days', NOW() - INTERVAL '100 days', NOW() - INTERVAL '100 days');

-- Insert arena round for old deleted portfolio
INSERT INTO arena_rounds (id, session_id, round_number, round_type, created_at)
VALUES ('66666666-0000-0000-0000-000000000001', '55555555-0000-0000-0000-000000000001', 1, 'ASSET', NOW() - INTERVAL '100 days');

-- Insert arena round choices
INSERT INTO arena_round_choices (round_id, asset_id)
VALUES
    ('66666666-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001'),
    ('66666666-0000-0000-0000-000000000001', 'dddddddd-0000-0000-0000-000000000001');

-- Update arena round with selected asset
UPDATE arena_rounds
SET selected_asset_id = 'cccccccc-0000-0000-0000-000000000001',
    picked_at = NOW() - INTERVAL '100 days'
WHERE id = '66666666-0000-0000-0000-000000000001';

-- Insert recent deleted portfolio (20 days ago) - should NOT be hard deleted
INSERT INTO portfolios (id, user_id, name, status, started_at, created_at, updated_at, deleted_at)
VALUES ('77777777-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', 'Recent Deleted Portfolio', 'ACTIVE',
        NOW() - INTERVAL '50 days', NOW() - INTERVAL '50 days', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days');

-- Insert active portfolio (not deleted) - should NOT be hard deleted
INSERT INTO portfolios (id, user_id, name, status, started_at, created_at, updated_at)
VALUES ('88888888-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001', 'Active Portfolio', 'ACTIVE',
        NOW() - INTERVAL '100 days', NOW() - INTERVAL '100 days', NOW());