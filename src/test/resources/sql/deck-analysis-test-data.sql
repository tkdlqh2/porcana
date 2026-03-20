-- Deck Analysis Engine 테스트용 데이터

-- 기존 테스트 데이터 정리
TRUNCATE TABLE asset_prices CASCADE;
TRUNCATE TABLE assets CASCADE;

-- ========== 스타일 판별용 자산 ==========

-- 고위험 성장 주식 (GROWTH role용)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'US', 'NVDA', 'NVIDIA Corporation', 'STOCK', 'INFORMATION_TECHNOLOGY', 5, true, CURRENT_DATE, NOW(), NOW()),
    ('a0000000-0000-0000-0000-000000000002', 'US', 'META', 'Meta Platforms', 'STOCK', 'COMMUNICATION_SERVICES', 4, true, CURRENT_DATE, NOW(), NOW()),
    ('a0000000-0000-0000-0000-000000000003', 'US', 'AAPL', 'Apple Inc.', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('a0000000-0000-0000-0000-000000000004', 'US', 'MSFT', 'Microsoft Corporation', 'STOCK', 'INFORMATION_TECHNOLOGY', 2, true, CURRENT_DATE, NOW(), NOW());

-- CORE 역할용 주식
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('b0000000-0000-0000-0000-000000000001', 'US', 'JPM', 'JPMorgan Chase', 'STOCK', 'FINANCIALS', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000002', 'US', 'JNJ', 'Johnson & Johnson', 'STOCK', 'HEALTH_CARE', 2, true, CURRENT_DATE, NOW(), NOW()),
    ('b0000000-0000-0000-0000-000000000003', 'US', 'PG', 'Procter & Gamble', 'STOCK', 'CONSUMER_STAPLES', 2, true, CURRENT_DATE, NOW(), NOW());

-- DEFENSIVE/HEDGE 역할용 ETF
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('c0000000-0000-0000-0000-000000000001', 'US', 'BND', 'Vanguard Total Bond Market ETF', 'ETF', 'BOND', 1, true, CURRENT_DATE, NOW(), NOW()),
    ('c0000000-0000-0000-0000-000000000002', 'US', 'GLD', 'SPDR Gold Shares', 'ETF', 'COMMODITY', 2, true, CURRENT_DATE, NOW(), NOW()),
    ('c0000000-0000-0000-0000-000000000003', 'US', 'TLT', 'iShares 20+ Year Treasury Bond ETF', 'ETF', 'BOND', 2, true, CURRENT_DATE, NOW(), NOW());

-- INCOME 역할용 자산
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES
    ('d0000000-0000-0000-0000-000000000001', 'US', 'VYM', 'Vanguard High Dividend Yield ETF', 'ETF', 'DIVIDEND', 2, true, CURRENT_DATE,
     true, 0.0350, 'HIGH_DIVIDEND', 'QUARTERLY', 'VERIFIED', NOW(), NOW()),
    ('d0000000-0000-0000-0000-000000000002', 'US', 'SCHD', 'Schwab US Dividend Equity ETF', 'ETF', 'DIVIDEND', 2, true, CURRENT_DATE,
     true, 0.0380, 'HIGH_DIVIDEND', 'QUARTERLY', 'VERIFIED', NOW(), NOW());

-- UTILITIES 섹터 (DEFENSIVE 역할, 배당 친화적)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('e0000000-0000-0000-0000-000000000001', 'US', 'NEE', 'NextEra Energy', 'STOCK', 'UTILITIES', 2, true, CURRENT_DATE, NOW(), NOW()),
    ('e0000000-0000-0000-0000-000000000002', 'US', 'SO', 'Southern Company', 'STOCK', 'UTILITIES', 2, true, CURRENT_DATE, NOW(), NOW());

-- ========== 시장별 분산 테스트용 ==========

-- 한국 시장 자산
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'KR', '005930', '삼성전자', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, CURRENT_DATE, NOW(), NOW()),
    ('f0000000-0000-0000-0000-000000000002', 'KR', '000660', 'SK하이닉스', 'STOCK', 'INFORMATION_TECHNOLOGY', 4, true, CURRENT_DATE, NOW(), NOW()),
    ('f0000000-0000-0000-0000-000000000003', 'KR', '035420', 'NAVER', 'STOCK', 'COMMUNICATION_SERVICES', 3, true, CURRENT_DATE, NOW(), NOW());

-- ========== 배당 관련 테스트용 ==========

-- INCOME_CORE 배당 프로필용
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES
    ('01000000-0000-0000-0000-000000000001', 'US', 'O', 'Realty Income Corp', 'STOCK', 'REAL_ESTATE', 2, true, CURRENT_DATE,
     true, 0.0550, 'HIGH_DIVIDEND', 'MONTHLY', 'VERIFIED', NOW(), NOW());

-- DIVIDEND_FOCUSED 배당 프로필용
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES
    ('01000000-0000-0000-0000-000000000002', 'US', 'KO', 'Coca-Cola Company', 'STOCK', 'CONSUMER_STAPLES', 2, true, CURRENT_DATE,
     true, 0.0280, 'DIVIDEND_GROWTH', 'QUARTERLY', 'VERIFIED', NOW(), NOW());

-- HAS_DIVIDEND 배당 프로필용
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES
    ('01000000-0000-0000-0000-000000000003', 'US', 'INTC', 'Intel Corporation', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, CURRENT_DATE,
     true, 0.0150, 'HAS_DIVIDEND', 'QUARTERLY', 'VERIFIED', NOW(), NOW());

-- ========== null 위험도 테스트용 ==========
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES
    ('02000000-0000-0000-0000-000000000001', 'US', 'NEWCO', 'New Company Inc.', 'STOCK', 'INFORMATION_TECHNOLOGY', NULL, true, CURRENT_DATE, NOW(), NOW());
