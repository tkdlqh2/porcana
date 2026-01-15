-- Create exchange_rates table for storing daily exchange rates
-- Data sourced from Korea Exim Bank API

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    currency_code VARCHAR(10) NOT NULL,
    currency_name VARCHAR(100) NOT NULL,
    base_rate NUMERIC(15, 2) NOT NULL,
    buy_rate NUMERIC(15, 2),
    sell_rate NUMERIC(15, 2),
    exchange_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create unique index on currency_code and exchange_date to prevent duplicates
CREATE UNIQUE INDEX idx_exchange_rate_currency_date ON exchange_rates(currency_code, exchange_date);

-- Create index on exchange_date for efficient date-based queries
CREATE INDEX idx_exchange_rate_date ON exchange_rates(exchange_date);

-- Add comments for documentation
COMMENT ON TABLE exchange_rates IS 'Daily exchange rates from Korea Exim Bank API';
COMMENT ON COLUMN exchange_rates.currency_code IS 'Currency code (USD, JPY, EUR, etc.)';
COMMENT ON COLUMN exchange_rates.currency_name IS 'Currency name in Korean';
COMMENT ON COLUMN exchange_rates.base_rate IS 'Base exchange rate (매매기준율) in KRW';
COMMENT ON COLUMN exchange_rates.buy_rate IS 'Buy rate (송금 받을 때) in KRW';
COMMENT ON COLUMN exchange_rates.sell_rate IS 'Sell rate (송금 보낼 때) in KRW';
COMMENT ON COLUMN exchange_rates.exchange_date IS 'Date of the exchange rate';
