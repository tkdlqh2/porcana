-- Test data for PortfolioControllerTest
-- Clean up existing data
DELETE FROM portfolio_assets WHERE portfolio_id IN (
    SELECT id FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440000'
);
DELETE FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440000';
DELETE FROM assets WHERE symbol IN ('TEST_KR', 'TEST_US');
DELETE FROM users WHERE email = 'portfolio-test@example.com';

-- Insert test user
INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'portfolio-test@example.com', 'password123', '포트폴리오테스터', 'EMAIL', NULL, NOW(), NOW());

-- Insert test assets
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, created_at, updated_at, as_of)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'TEST_KR', '테스트한국주식', 'KR', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, NULL, NOW(), NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', 'TEST_US', 'Test US Stock', 'US', 'STOCK', 'HEALTH_CARE', 4, true, NULL, NOW(), NOW(), NOW());

-- Insert test portfolio (ACTIVE)
INSERT INTO portfolios (id, user_id, guest_session_id, name, status, started_at, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333333', '550e8400-e29b-41d4-a716-446655440000', NULL, '테스트 포트폴리오', 'ACTIVE', '2024-01-01', NOW(), NOW());

-- Insert portfolio assets
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct)
VALUES
    ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 50.00),
    ('55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 50.00);