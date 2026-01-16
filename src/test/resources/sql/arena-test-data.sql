-- Clean up existing test data
TRUNCATE TABLE arena_rounds CASCADE;
TRUNCATE TABLE arena_sessions CASCADE;
TRUNCATE TABLE portfolio_assets CASCADE;
TRUNCATE TABLE portfolios CASCADE;
TRUNCATE TABLE asset_prices CASCADE;
TRUNCATE TABLE assets CASCADE;
TRUNCATE TABLE users CASCADE;

-- User 생성
INSERT INTO users (id, email, password, nickname, provider, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'test@example.com', 'password123', '테스터', 'EMAIL', NOW(), NOW());

-- Portfolio 생성
INSERT INTO portfolios (id, user_id, name, status, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', '테스트 포트폴리오', 'DRAFT', NOW(), NOW());

-- 테스트용 Asset 데이터 생성 (다양한 섹터와 리스크 레벨)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    -- IT 섹터
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'US', 'AAPL', 'Apple Inc.', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab', 'US', 'MSFT', 'Microsoft Corp.', 'STOCK', 'INFORMATION_TECHNOLOGY', 2, true, CURRENT_DATE, NOW(), NOW()),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaac', 'US', 'NVDA', 'NVIDIA Corp.', 'STOCK', 'INFORMATION_TECHNOLOGY', 4, true, CURRENT_DATE, NOW(), NOW()),

    -- Healthcare 섹터
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'US', 'JNJ', 'Johnson & Johnson', 'STOCK', 'HEALTH_CARE', 2, true, CURRENT_DATE, NOW(), NOW()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbc', 'US', 'PFE', 'Pfizer Inc.', 'STOCK', 'HEALTH_CARE', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbd', 'US', 'UNH', 'UnitedHealth Group', 'STOCK', 'HEALTH_CARE', 2, true, CURRENT_DATE, NOW(), NOW()),

    -- Financials 섹터
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'US', 'JPM', 'JPMorgan Chase', 'STOCK', 'FINANCIALS', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('cccccccc-cccc-cccc-cccc-cccccccccccd', 'US', 'BAC', 'Bank of America', 'STOCK', 'FINANCIALS', 4, true, CURRENT_DATE, NOW(), NOW()),
    ('cccccccc-cccc-cccc-cccc-ccccccccccce', 'US', 'WFC', 'Wells Fargo', 'STOCK', 'FINANCIALS', 4, true, CURRENT_DATE, NOW(), NOW()),

    -- Energy 섹터
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'US', 'XOM', 'Exxon Mobil', 'STOCK', 'ENERGY', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('dddddddd-dddd-dddd-dddd-ddddddddddde', 'US', 'CVX', 'Chevron Corp.', 'STOCK', 'ENERGY', 3, true, CURRENT_DATE, NOW(), NOW()),

    -- Consumer Discretionary 섹터
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'US', 'AMZN', 'Amazon.com Inc.', 'STOCK', 'CONSUMER_DISCRETIONARY', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeef', 'US', 'TSLA', 'Tesla Inc.', 'STOCK', 'CONSUMER_DISCRETIONARY', 5, true, CURRENT_DATE, NOW(), NOW()),

    -- Consumer Staples 섹터
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'US', 'PG', 'Procter & Gamble', 'STOCK', 'CONSUMER_STAPLES', 1, true, CURRENT_DATE, NOW(), NOW()),
    ('ffffffff-ffff-ffff-ffff-fffffffffffe', 'US', 'KO', 'Coca-Cola Co.', 'STOCK', 'CONSUMER_STAPLES', 1, true, CURRENT_DATE, NOW(), NOW()),

    -- Industrials 섹터
    ('99999999-9999-9999-9999-999999999999', 'US', 'BA', 'Boeing Co.', 'STOCK', 'INDUSTRIALS', 4, true, CURRENT_DATE, NOW(), NOW()),
    ('99999999-9999-9999-9999-999999999998', 'US', 'CAT', 'Caterpillar Inc.', 'STOCK', 'INDUSTRIALS', 3, true, CURRENT_DATE, NOW(), NOW()),

    -- Materials 섹터
    ('88888888-8888-8888-8888-888888888888', 'US', 'LIN', 'Linde plc', 'STOCK', 'MATERIALS', 2, true, CURRENT_DATE, NOW(), NOW()),

    -- Utilities 섹터
    ('77777777-7777-7777-7777-777777777777', 'US', 'NEE', 'NextEra Energy', 'STOCK', 'UTILITIES', 2, true, CURRENT_DATE, NOW(), NOW()),

    -- Real Estate 섹터
    ('66666666-6666-6666-6666-666666666666', 'US', 'AMT', 'American Tower', 'STOCK', 'REAL_ESTATE', 2, true, CURRENT_DATE, NOW(), NOW()),

    -- Communication Services 섹터
    ('55555555-5555-5555-5555-555555555555', 'US', 'GOOGL', 'Alphabet Inc.', 'STOCK', 'COMMUNICATION_SERVICES', 3, true, CURRENT_DATE, NOW(), NOW());