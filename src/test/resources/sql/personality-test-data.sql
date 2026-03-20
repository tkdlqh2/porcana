-- Asset Personality Rule Engine 테스트용 데이터

-- 기존 테스트 데이터 정리
TRUNCATE TABLE asset_prices CASCADE;
TRUNCATE TABLE assets CASCADE;

-- ========== ETF 테스트 데이터 ==========

-- EQUITY_INDEX ETF (저위험, risk=2)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'US', 'SPY', 'SPDR S&P 500 ETF', 'ETF', 'EQUITY_INDEX', 2, true, CURRENT_DATE, NOW(), NOW());

-- EQUITY_INDEX ETF (중위험, risk=3)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000002', 'US', 'QQQ', 'Invesco QQQ Trust', 'ETF', 'EQUITY_INDEX', 3, true, CURRENT_DATE, NOW(), NOW());

-- DIVIDEND ETF (저위험, risk=2)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000003', 'US', 'VYM', 'Vanguard High Dividend Yield ETF', 'ETF', 'DIVIDEND', 2, true, CURRENT_DATE, NOW(), NOW());

-- BOND ETF (저위험, risk=1)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000004', 'US', 'BND', 'Vanguard Total Bond Market ETF', 'ETF', 'BOND', 1, true, CURRENT_DATE, NOW(), NOW());

-- BOND ETF (고위험, risk=4)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000005', 'US', 'HYG', 'iShares iBoxx High Yield Corp Bond ETF', 'ETF', 'BOND', 4, true, CURRENT_DATE, NOW(), NOW());

-- COMMODITY ETF (중위험, risk=3)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000006', 'US', 'GLD', 'SPDR Gold Shares', 'ETF', 'COMMODITY', 3, true, CURRENT_DATE, NOW(), NOW());

-- SECTOR ETF (중위험, risk=3)
INSERT INTO assets (id, market, symbol, name, type, asset_class, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000007', 'US', 'XLK', 'Technology Select Sector SPDR', 'ETF', 'SECTOR', 3, true, CURRENT_DATE, NOW(), NOW());

-- ========== 주식 테스트 데이터 ==========

-- 고위험 IT 주식 (risk=5)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000001', 'US', 'NVDA', 'NVIDIA Corporation', 'STOCK', 'INFORMATION_TECHNOLOGY', 5, true, CURRENT_DATE, NOW(), NOW());

-- 중위험 IT 주식 (risk=3)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000002', 'US', 'AAPL', 'Apple Inc.', 'STOCK', 'INFORMATION_TECHNOLOGY', 3, true, CURRENT_DATE, NOW(), NOW());

-- 저위험 방어 섹터 주식 - Healthcare (risk=2) - 배당 친화적 섹터
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000003', 'US', 'JNJ', 'Johnson & Johnson', 'STOCK', 'HEALTH_CARE', 2, true, CURRENT_DATE, NOW(), NOW());

-- 저위험 일반 섹터 주식 - IT (risk=1)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000004', 'US', 'MSFT', 'Microsoft Corporation', 'STOCK', 'INFORMATION_TECHNOLOGY', 1, true, CURRENT_DATE, NOW(), NOW());

-- 배당 친화적 섹터 + 저위험 - Utilities (risk=2)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000005', 'US', 'NEE', 'NextEra Energy', 'STOCK', 'UTILITIES', 2, true, CURRENT_DATE, NOW(), NOW());

-- 고위험 IT 주식 (risk=4) - 배당 프로필 NONE 테스트용
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of, created_at, updated_at)
VALUES ('20000000-0000-0000-0000-000000000006', 'US', 'TSLA', 'Tesla Inc.', 'STOCK', 'INFORMATION_TECHNOLOGY', 4, true, CURRENT_DATE, NOW(), NOW());

-- ========== 배당 데이터가 있는 주식 ==========

-- HIGH_DIVIDEND + VERIFIED - Financials (risk=2)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000001', 'US', 'JPM', 'JPMorgan Chase', 'STOCK', 'FINANCIALS', 2, true, CURRENT_DATE,
        true, 0.0500, 'HIGH_DIVIDEND', 'QUARTERLY', 'VERIFIED', NOW(), NOW());

-- DIVIDEND_GROWTH + VERIFIED - Consumer Staples (risk=2)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000002', 'US', 'PG', 'Procter & Gamble', 'STOCK', 'CONSUMER_STAPLES', 2, true, CURRENT_DATE,
        true, 0.0250, 'DIVIDEND_GROWTH', 'QUARTERLY', 'VERIFIED', NOW(), NOW());

-- 높은 배당수익률 + 월배당 - Real Estate (risk=2)
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000003', 'US', 'O', 'Realty Income Corp', 'STOCK', 'REAL_ESTATE', 2, true, CURRENT_DATE,
        true, 0.0450, 'HAS_DIVIDEND', 'MONTHLY', 'VERIFIED', NOW(), NOW());

-- HIGH_DIVIDEND + VERIFIED - Utilities (risk=2) - INCOME_CORE 테스트용
INSERT INTO assets (id, market, symbol, name, type, sector, current_risk_level, active, as_of,
                    dividend_available, dividend_yield, dividend_category, dividend_frequency, dividend_data_status,
                    created_at, updated_at)
VALUES ('30000000-0000-0000-0000-000000000004', 'US', 'SO', 'Southern Company', 'STOCK', 'UTILITIES', 2, true, CURRENT_DATE,
        true, 0.0600, 'HIGH_DIVIDEND', 'QUARTERLY', 'VERIFIED', NOW(), NOW());
