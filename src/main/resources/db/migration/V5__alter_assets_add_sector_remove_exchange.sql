-- Add sector column to assets table
ALTER TABLE assets ADD COLUMN sector VARCHAR(100);

-- Drop exchange column from assets table
ALTER TABLE assets DROP COLUMN IF EXISTS exchange;