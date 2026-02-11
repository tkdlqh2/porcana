-- Test data for guest portfolio tests
-- Clean up existing data
DELETE FROM portfolio_assets WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE guest_session_id = 'aaa00000-0000-0000-0000-000000000001'
);
DELETE FROM portfolios WHERE guest_session_id = 'aaa00000-0000-0000-0000-000000000001';
DELETE FROM guest_sessions WHERE id = 'aaa00000-0000-0000-0000-000000000001';
DELETE FROM assets WHERE symbol IN ('GUEST_TEST_KR', 'GUEST_TEST_US');

-- Insert test guest session
INSERT INTO guest_sessions (id, created_at, last_seen_at)
VALUES ('aaa00000-0000-0000-0000-000000000001', NOW(), NOW());

-- Insert test assets
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, created_at, updated_at, as_of)
VALUES
    ('ccc00000-0000-0000-0000-000000000001', 'GUEST_TEST_KR', '게스트테스트한국주식', 'KR', 'STOCK', 'FINANCIALS', 2, true, NULL, NOW(), NOW(), NOW()),
    ('ddd00000-0000-0000-0000-000000000001', 'GUEST_TEST_US', 'Guest Test US Stock', 'US', 'STOCK', 'ENERGY', 5, true, NULL, NOW(), NOW(), NOW());

-- Insert test guest portfolio (ACTIVE)
INSERT INTO portfolios (id, user_id, guest_session_id, name, status, started_at, created_at, updated_at)
VALUES ('bbb00000-0000-0000-0000-000000000001', NULL, 'aaa00000-0000-0000-0000-000000000001', '게스트 테스트 포트폴리오', 'ACTIVE', '2024-01-01', NOW(), NOW());

-- Insert portfolio assets
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct)
VALUES
    ('eee00000-0000-0000-0000-000000000001', 'bbb00000-0000-0000-0000-000000000001', 'ccc00000-0000-0000-0000-000000000001', 60.00),
    ('fff00000-0000-0000-0000-000000000001', 'bbb00000-0000-0000-0000-000000000001', 'ddd00000-0000-0000-0000-000000000001', 40.00);