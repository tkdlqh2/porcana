-- Test data for HoldingBaselineControllerTest
-- Clean up existing data
DELETE FROM portfolio_holding_baseline_items WHERE baseline_id IN (
    SELECT id FROM portfolio_holding_baselines WHERE portfolio_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);
DELETE FROM portfolio_holding_baselines WHERE portfolio_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DELETE FROM portfolio_assets WHERE portfolio_id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DELETE FROM portfolios WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
DELETE FROM asset_prices WHERE asset_id IN ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab');
DELETE FROM assets WHERE symbol IN ('BASELINE_KR', 'BASELINE_US');
DELETE FROM users WHERE email = 'baseline-test@example.com';
DELETE FROM exchange_rates WHERE currency_code = 'USD';

-- Insert test user
INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'baseline-test@example.com', 'password123', 'baseline테스터', 'EMAIL', NULL, NOW(), NOW());

-- Insert test assets
INSERT INTO assets (id, symbol, name, market, type, sector, current_risk_level, active, image_url, created_at, updated_at, as_of)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'BASELINE_KR', '테스트한국주식', 'KR', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, NULL, NOW(), NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 'BASELINE_US', 'Test US Stock', 'US', 'STOCK', 'HEALTH_CARE', 4, true, NULL, NOW(), NOW(), NOW());

-- Insert asset prices (최신 가격)
INSERT INTO asset_prices (id, asset_id, price_date, open_price, high_price, low_price, close_price, volume, created_at)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', CURRENT_DATE, 70000, 72000, 69000, 71000, 1000000, NOW()),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeef', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', CURRENT_DATE, 230, 235, 228, 232, 500000, NOW());

-- Insert exchange rate (USD -> KRW)
INSERT INTO exchange_rates (id, currency_code, currency_name, base_rate, buy_rate, sell_rate, exchange_date, created_at)
VALUES ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'USD', '미국 달러', 1350.00, 1360.00, 1340.00, CURRENT_DATE, NOW());

-- Insert test portfolio (ACTIVE)
INSERT INTO portfolios (id, user_id, guest_session_id, name, status, started_at, created_at, updated_at)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'cccccccc-cccc-cccc-cccc-cccccccccccc', NULL, 'Baseline 테스트 포트폴리오', 'ACTIVE', '2024-01-01', NOW(), NOW());

-- Insert portfolio assets (50% KR, 50% US)
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, weight_pct)
VALUES
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 50.00),
    ('ffffffff-ffff-ffff-ffff-fffffffffffe', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 50.00);
