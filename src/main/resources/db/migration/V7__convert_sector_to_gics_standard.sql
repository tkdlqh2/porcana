-- Convert sector values from FMP names to GICS standard enum values
-- Mapping: FMP name -> GICS standard

UPDATE assets SET sector = 'MATERIALS' WHERE sector = 'Basic Materials';
UPDATE assets SET sector = 'COMMUNICATION_SERVICES' WHERE sector = 'Communication Services';
UPDATE assets SET sector = 'CONSUMER_DISCRETIONARY' WHERE sector = 'Consumer Cyclical';
UPDATE assets SET sector = 'CONSUMER_STAPLES' WHERE sector = 'Consumer Defensive';
UPDATE assets SET sector = 'ENERGY' WHERE sector = 'Energy';
UPDATE assets SET sector = 'FINANCIALS' WHERE sector = 'Financial Services';
UPDATE assets SET sector = 'HEALTH_CARE' WHERE sector = 'Healthcare';
UPDATE assets SET sector = 'INDUSTRIALS' WHERE sector = 'Industrials';
UPDATE assets SET sector = 'REAL_ESTATE' WHERE sector = 'Real Estate';
UPDATE assets SET sector = 'INFORMATION_TECHNOLOGY' WHERE sector = 'Technology';
UPDATE assets SET sector = 'UTILITIES' WHERE sector = 'Utilities';

-- Alter column length from VARCHAR(100) to VARCHAR(50)
-- This is safe because GICS enum values are shorter than 50 characters
ALTER TABLE assets MODIFY COLUMN sector VARCHAR(50);