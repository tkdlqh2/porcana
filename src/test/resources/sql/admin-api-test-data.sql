-- Test data for admin API integration tests

DELETE FROM snapshot_asset_daily_returns WHERE portfolio_id IN (
    'a7490f7e-8596-41ff-abb0-ff06894928f2',
    'b7490f7e-8596-41ff-abb0-ff06894928f2',
    'c7490f7e-8596-41ff-abb0-ff06894928f2'
);
DELETE FROM portfolio_snapshot_assets WHERE snapshot_id IN (
    'aa111111-1111-1111-1111-111111111111'
);
DELETE FROM portfolio_daily_returns WHERE portfolio_id IN (
    'a7490f7e-8596-41ff-abb0-ff06894928f2',
    'b7490f7e-8596-41ff-abb0-ff06894928f2',
    'c7490f7e-8596-41ff-abb0-ff06894928f2'
);
DELETE FROM portfolio_snapshots WHERE id IN (
    'aa111111-1111-1111-1111-111111111111'
);
DELETE FROM portfolio_assets WHERE portfolio_id IN (
    'a7490f7e-8596-41ff-abb0-ff06894928f2',
    'b7490f7e-8596-41ff-abb0-ff06894928f2',
    'c7490f7e-8596-41ff-abb0-ff06894928f2'
);
DELETE FROM portfolios WHERE id IN (
    'a7490f7e-8596-41ff-abb0-ff06894928f2',
    'b7490f7e-8596-41ff-abb0-ff06894928f2',
    'c7490f7e-8596-41ff-abb0-ff06894928f2'
);
DELETE FROM asset_prices WHERE asset_id IN (
    'f1111111-1111-1111-1111-111111111111',
    'f2222222-2222-2222-2222-222222222222'
);
DELETE FROM assets WHERE id IN (
    'f1111111-1111-1111-1111-111111111111',
    'f2222222-2222-2222-2222-222222222222'
);
DELETE FROM users WHERE id IN (
    '91000000-0000-0000-0000-000000000001',
    '92000000-0000-0000-0000-000000000001'
);

INSERT INTO users (id, email, password, nickname, provider, role, created_at, updated_at)
VALUES
    ('91000000-0000-0000-0000-000000000001', 'admin-test@example.com', 'password123', 'Admin Tester', 'EMAIL', 'ADMIN', NOW(), NOW()),
    ('92000000-0000-0000-0000-000000000001', 'owner-test@example.com', 'password123', 'Owner Tester', 'EMAIL', 'USER', NOW(), NOW());

INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, description, created_at, updated_at, as_of)
VALUES
    ('f1111111-1111-1111-1111-111111111111', 'ADMIN_KR', 'Admin KR Asset', 'KR', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, 'https://example.com/kr.png', 'KR admin asset description', NOW(), NOW(), NOW()),
    ('f2222222-2222-2222-2222-222222222222', 'ADMIN_US', 'Admin US Asset', 'US', 'ETF', 'HEALTH_CARE', 4, true, 'https://example.com/us.png', 'US admin asset description', NOW(), NOW(), NOW());

INSERT INTO asset_prices (id, asset_id, price_date, open_price, high_price, low_price, close_price, volume, created_at)
VALUES
    ('e1111111-1111-1111-1111-111111111111', 'f1111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '2 days', 10000, 10100, 9900, 10050, 1000000, NOW()),
    ('e2222222-2222-2222-2222-222222222222', 'f1111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '1 day', 10050, 10200, 10000, 10150, 1200000, NOW()),
    ('e3333333-3333-3333-3333-333333333333', 'f2222222-2222-2222-2222-222222222222', CURRENT_DATE - INTERVAL '2 days', 200, 205, 198, 204, 2000000, NOW()),
    ('e4444444-4444-4444-4444-444444444444', 'f2222222-2222-2222-2222-222222222222', CURRENT_DATE - INTERVAL '1 day', 204, 207, 203, 206, 1800000, NOW());

INSERT INTO portfolios (id, user_id, guest_session_id, name, status, started_at, created_at, updated_at)
VALUES
    ('a7490f7e-8596-41ff-abb0-ff06894928f2', '92000000-0000-0000-0000-000000000001', NULL, 'Admin Active Portfolio', 'ACTIVE', CURRENT_DATE - INTERVAL '5 days', NOW() - INTERVAL '5 days', NOW()),
    ('b7490f7e-8596-41ff-abb0-ff06894928f2', '92000000-0000-0000-0000-000000000001', NULL, 'Admin Finished Portfolio', 'FINISHED', CURRENT_DATE - INTERVAL '20 days', NOW() - INTERVAL '20 days', NOW()),
    ('c7490f7e-8596-41ff-abb0-ff06894928f2', '92000000-0000-0000-0000-000000000001', NULL, 'Admin Draft Portfolio', 'DRAFT', NULL, NOW() - INTERVAL '1 day', NOW());

INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct)
VALUES
    ('fa111111-1111-1111-1111-111111111111', 'a7490f7e-8596-41ff-abb0-ff06894928f2', 'f1111111-1111-1111-1111-111111111111', 60.00),
    ('fa222222-2222-2222-2222-222222222222', 'a7490f7e-8596-41ff-abb0-ff06894928f2', 'f2222222-2222-2222-2222-222222222222', 40.00);

INSERT INTO portfolio_snapshots (id, portfolio_id, effective_date, note, created_at)
VALUES
    ('aa111111-1111-1111-1111-111111111111', 'a7490f7e-8596-41ff-abb0-ff06894928f2', CURRENT_DATE - INTERVAL '5 days', 'Initial admin test snapshot', NOW() - INTERVAL '5 days');

INSERT INTO portfolio_daily_returns (id, portfolio_id, snapshot_id, return_date, return_total, return_local, return_fx, total_value_krw, calculated_at)
VALUES
    ('fb111111-1111-1111-1111-111111111111', 'a7490f7e-8596-41ff-abb0-ff06894928f2', 'aa111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '3 days', 1.5000, 1.3000, 0.2000, 10150000.00, NOW() - INTERVAL '3 days'),
    ('fb222222-2222-2222-2222-222222222222', 'a7490f7e-8596-41ff-abb0-ff06894928f2', 'aa111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '2 days', 2.5000, 2.1000, 0.4000, 10250000.00, NOW() - INTERVAL '2 days'),
    ('fb333333-3333-3333-3333-333333333333', 'a7490f7e-8596-41ff-abb0-ff06894928f2', 'aa111111-1111-1111-1111-111111111111', CURRENT_DATE - INTERVAL '1 day', 4.0000, 3.2000, 0.8000, 10400000.00, NOW() - INTERVAL '1 day');
