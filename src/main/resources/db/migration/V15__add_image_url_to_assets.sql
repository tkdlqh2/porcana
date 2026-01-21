-- Add image_url column to assets table for company logos (US stocks only)

ALTER TABLE assets
ADD COLUMN image_url VARCHAR(500);

COMMENT ON COLUMN assets.image_url IS 'Company logo image URL (from FMP API for US stocks)';