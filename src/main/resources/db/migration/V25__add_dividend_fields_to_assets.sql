-- Asset 테이블에 배당 관련 필드 추가

-- 배당 여부
ALTER TABLE assets ADD COLUMN dividend_available BOOLEAN;

-- 배당수익률 (소수 기준, 예: 0.0350 = 3.50%)
ALTER TABLE assets ADD COLUMN dividend_yield DECIMAL(10, 6);

-- 배당 주기
ALTER TABLE assets ADD COLUMN dividend_frequency VARCHAR(20);

-- 배당 성향 태그
ALTER TABLE assets ADD COLUMN dividend_category VARCHAR(30);

-- 배당 데이터 수집 상태
ALTER TABLE assets ADD COLUMN dividend_data_status VARCHAR(20);

-- 최근 배당 기준일
ALTER TABLE assets ADD COLUMN last_dividend_date DATE;

-- 인덱스: 배당 관련 조회 최적화
CREATE INDEX idx_assets_dividend_available ON assets (dividend_available) WHERE dividend_available = true;
CREATE INDEX idx_assets_dividend_yield ON assets (dividend_yield) WHERE dividend_yield IS NOT NULL;
CREATE INDEX idx_assets_dividend_category ON assets (dividend_category) WHERE dividend_category IS NOT NULL;

COMMENT ON COLUMN assets.dividend_available IS '배당 지급 여부';
COMMENT ON COLUMN assets.dividend_yield IS '연환산 배당수익률 (소수 기준)';
COMMENT ON COLUMN assets.dividend_frequency IS '배당 주기 (NONE, MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL, IRREGULAR, UNKNOWN)';
COMMENT ON COLUMN assets.dividend_category IS '배당 성향 (NONE, HAS_DIVIDEND, DIVIDEND_GROWTH, HIGH_DIVIDEND, REIT_LIKE, COVERED_CALL_LIKE, UNKNOWN)';
COMMENT ON COLUMN assets.dividend_data_status IS '배당 데이터 수집 상태 (NONE, PARTIAL, VERIFIED)';
COMMENT ON COLUMN assets.last_dividend_date IS '최근 배당 기준일';
