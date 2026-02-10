-- Test data for portfolio weight update after daily returns are accumulated
-- Simulates a portfolio that has been running for several days with market changes

-- Clean up existing data
DELETE FROM snapshot_asset_daily_returns WHERE portfolio_id = '77777777-7777-7777-7777-777777777777';
DELETE FROM portfolio_daily_returns WHERE portfolio_id = '77777777-7777-7777-7777-777777777777';
DELETE FROM portfolio_snapshot_assets WHERE snapshot_id IN (
    SELECT id FROM portfolio_snapshots WHERE portfolio_id = '77777777-7777-7777-7777-777777777777'
);
DELETE FROM portfolio_snapshots WHERE portfolio_id = '77777777-7777-7777-7777-777777777777';
DELETE FROM portfolio_assets WHERE portfolio_id = '77777777-7777-7777-7777-777777777777';
DELETE FROM portfolios WHERE id = '77777777-7777-7777-7777-777777777777';
DELETE FROM assets WHERE symbol IN ('TEST_KR_2', 'TEST_US_2');
DELETE FROM users WHERE email = 'portfolio-daily-return-test@example.com';

-- Insert test user
INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at)
VALUES ('660e8400-e29b-41d4-a716-446655440000', 'portfolio-daily-return-test@example.com', 'password123', '일별수익테스터', 'EMAIL', NULL, NOW(), NOW());

-- Insert test assets
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, created_at, updated_at, as_of)
VALUES
    ('88888888-8888-8888-8888-888888888888', 'TEST_KR_2', '테스트한국주식2', 'KR', 'STOCK', 'FINANCIALS', 2, true, NULL, NOW(), NOW(), NOW()),
    ('99999999-9999-9999-9999-999999999999', 'TEST_US_2', 'Test US Stock 2', 'US', 'STOCK', 'CONSUMER_DISCRETIONARY', 3, true, NULL, NOW(), NOW(), NOW());

-- Insert test portfolio (ACTIVE, started 5 days ago)
INSERT INTO portfolios (id, user_id, guest_session_id, name, status, started_at, created_at, updated_at)
VALUES ('77777777-7777-7777-7777-777777777777', '660e8400-e29b-41d4-a716-446655440000', NULL, '일별수익 테스트 포트폴리오', 'ACTIVE', CURRENT_DATE - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW());

-- Insert portfolio assets (initial 50/50)
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '77777777-7777-7777-7777-777777777777', '88888888-8888-8888-8888-888888888888', 50.00),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '77777777-7777-7777-7777-777777777777', '99999999-9999-9999-9999-999999999999', 50.00);

-- Insert portfolio snapshot (initial snapshot from 5 days ago)
INSERT INTO portfolio_snapshots (id, portfolio_id, effective_date, note, created_at)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', '77777777-7777-7777-7777-777777777777', CURRENT_DATE - INTERVAL '5 days', 'Initial creation', NOW() - INTERVAL '5 days');

-- Insert portfolio snapshot assets
INSERT INTO portfolio_snapshot_assets (id, snapshot_id, asset_id, weight)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', 50.00),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '99999999-9999-9999-9999-999999999999', 50.00);

-- Insert portfolio daily returns for the last 3 days
-- Day 1: Small positive return
INSERT INTO portfolio_daily_returns (id, portfolio_id, snapshot_id, return_date, return_total, return_local, return_fx, total_value_krw, calculated_at)
VALUES ('11111111-1111-1111-1111-111111111111', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', CURRENT_DATE - INTERVAL '3 days', 1.5000, 1.2000, 0.3000, 10150000.00, NOW() - INTERVAL '3 days');

-- Day 2: KR outperforms US
INSERT INTO portfolio_daily_returns (id, portfolio_id, snapshot_id, return_date, return_total, return_local, return_fx, total_value_krw, calculated_at)
VALUES ('22222222-2222-2222-2222-222222222222', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', CURRENT_DATE - INTERVAL '2 days', 3.2000, 2.8000, 0.4000, 10320000.00, NOW() - INTERVAL '2 days');

-- Day 3 (latest): More gains, KR continues to outperform
INSERT INTO portfolio_daily_returns (id, portfolio_id, snapshot_id, return_date, return_total, return_local, return_fx, total_value_krw, calculated_at)
VALUES ('33333333-3333-3333-3333-333333333333', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', CURRENT_DATE - INTERVAL '1 day', 5.0000, 4.5000, 0.5000, 10500000.00, NOW() - INTERVAL '1 day');

-- Insert snapshot asset daily returns
-- Day 1: Both assets perform equally (still 50/50 weight)
INSERT INTO snapshot_asset_daily_returns (id, portfolio_id, snapshot_id, asset_id, return_date, weight_used, asset_return_local, asset_return_total, fx_return, contribution_total, value_krw, calculated_at)
VALUES
    ('d1111111-1111-1111-1111-111111111111', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', CURRENT_DATE - INTERVAL '3 days', 50.00, 1.2000, 1.2000, 0.0000, 0.6000, 5060000.00, NOW() - INTERVAL '3 days'),
    ('d2222222-2222-2222-2222-222222222222', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '99999999-9999-9999-9999-999999999999', CURRENT_DATE - INTERVAL '3 days', 50.00, 1.5000, 1.8000, 0.3000, 0.9000, 5090000.00, NOW() - INTERVAL '3 days');

-- Day 2: KR outperforms, weight shifts to 52/48
INSERT INTO snapshot_asset_daily_returns (id, portfolio_id, snapshot_id, asset_id, return_date, weight_used, asset_return_local, asset_return_total, fx_return, contribution_total, value_krw, calculated_at)
VALUES
    ('d3333333-3333-3333-3333-333333333333', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', CURRENT_DATE - INTERVAL '2 days', 52.00, 3.5000, 3.5000, 0.0000, 1.8200, 5360000.00, NOW() - INTERVAL '2 days'),
    ('d4444444-4444-4444-4444-444444444444', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '99999999-9999-9999-9999-999999999999', CURRENT_DATE - INTERVAL '2 days', 48.00, 2.0000, 2.4000, 0.4000, 1.1520, 4960000.00, NOW() - INTERVAL '2 days');

-- Day 3 (latest): Weight further shifts to 55/45 due to continued KR outperformance
INSERT INTO snapshot_asset_daily_returns (id, portfolio_id, snapshot_id, asset_id, return_date, weight_used, asset_return_local, asset_return_total, fx_return, contribution_total, value_krw, calculated_at)
VALUES
    ('d5555555-5555-5555-5555-555555555555', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '88888888-8888-8888-8888-888888888888', CURRENT_DATE - INTERVAL '1 day', 55.00, 6.0000, 6.0000, 0.0000, 3.3000, 5775000.00, NOW() - INTERVAL '1 day'),
    ('d6666666-6666-6666-6666-666666666666', '77777777-7777-7777-7777-777777777777', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '99999999-9999-9999-9999-999999999999', CURRENT_DATE - INTERVAL '1 day', 45.00, 3.5000, 4.0000, 0.5000, 1.8000, 4725000.00, NOW() - INTERVAL '1 day');