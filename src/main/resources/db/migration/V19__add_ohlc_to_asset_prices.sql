-- Add OHLC (Open, High, Low, Close) columns to asset_prices table
-- This migration converts the single 'price' column to proper OHLC candlestick data

-- Step 1: Add new OHLC columns (nullable for now)
ALTER TABLE asset_prices
    ADD COLUMN open_price NUMERIC(20,4),
    ADD COLUMN high_price NUMERIC(20,4),
    ADD COLUMN low_price NUMERIC(20,4),
    ADD COLUMN close_price NUMERIC(20,4);

-- Step 2: Migrate existing price data to OHLC columns
-- For existing data, we set all OHLC values to the existing price
-- (This is a simplification - in production, you'd fetch real OHLC data)
UPDATE asset_prices
SET open_price = price,
    high_price = price,
    low_price = price,
    close_price = price
WHERE open_price IS NULL;

-- Step 3: Make OHLC columns NOT NULL
ALTER TABLE asset_prices
    ALTER COLUMN open_price SET NOT NULL,
    ALTER COLUMN high_price SET NOT NULL,
    ALTER COLUMN low_price SET NOT NULL,
    ALTER COLUMN close_price SET NOT NULL;

-- Step 4: Drop the old price column
ALTER TABLE asset_prices DROP COLUMN price;

-- Note: Indexes remain the same as they don't depend on the price column