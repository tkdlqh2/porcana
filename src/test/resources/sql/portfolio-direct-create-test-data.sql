-- Test data for Portfolio Direct Create API Test
-- Clean up existing data
DELETE FROM portfolio_snapshot_assets WHERE snapshot_id IN (
    SELECT id FROM portfolio_snapshots WHERE portfolio_id IN (
        SELECT id FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440001'
    )
);
DELETE FROM portfolio_snapshots WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440001'
);
DELETE FROM portfolio_assets WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440001'
);
DELETE FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440001';
DELETE FROM assets WHERE symbol LIKE 'DIRECT_TEST_%';
DELETE FROM users WHERE email = 'direct-create-test@example.com';
DELETE FROM guest_sessions WHERE id = 'ccc00000-0000-0000-0000-000000000001';

-- Insert test user
INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440001', 'direct-create-test@example.com', 'password123', '직접생성테스터', 'EMAIL', NULL, NOW(), NOW());

-- Insert guest session
INSERT INTO guest_sessions (id, last_seen_at, created_at)
VALUES ('ccc00000-0000-0000-0000-000000000001', NOW(), NOW());

-- Insert test assets (7 assets for testing min 5, max 20)
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, created_at, updated_at, as_of)
VALUES
    ('d1111111-1111-1111-1111-111111111111', 'DIRECT_TEST_1', '테스트종목1', 'KR', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, NULL, NOW(), NOW(), NOW()),
    ('d2222222-2222-2222-2222-222222222222', 'DIRECT_TEST_2', '테스트종목2', 'KR', 'STOCK', 'HEALTH_CARE', 4, true, NULL, NOW(), NOW(), NOW()),
    ('d3333333-3333-3333-3333-333333333333', 'DIRECT_TEST_3', '테스트종목3', 'US', 'STOCK', 'FINANCIALS', 2, true, NULL, NOW(), NOW(), NOW()),
    ('d4444444-4444-4444-4444-444444444444', 'DIRECT_TEST_4', '테스트종목4', 'US', 'STOCK', 'CONSUMER_DISCRETIONARY', 5, true, NULL, NOW(), NOW(), NOW()),
    ('d5555555-5555-5555-5555-555555555555', 'DIRECT_TEST_5', '테스트종목5', 'KR', 'ETF', NULL, 1, true, NULL, NOW(), NOW(), NOW()),
    ('d6666666-6666-6666-6666-666666666666', 'DIRECT_TEST_6', '테스트종목6', 'US', 'ETF', NULL, 2, true, NULL, NOW(), NOW(), NOW()),
    ('d7777777-7777-7777-7777-777777777777', 'DIRECT_TEST_7', '테스트종목7', 'KR', 'STOCK', 'INDUSTRIALS', 3, true, NULL, NOW(), NOW(), NOW());
