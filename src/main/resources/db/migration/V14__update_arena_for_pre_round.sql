-- Update arena tables for PRE_ROUND (Round 0) implementation
-- Changes:
-- 1. arena_sessions: current_round range 1-12 → 0-10 (Round 0 = Pre Round, Rounds 1-10 = Assets)
-- 2. arena_sessions: total_rounds default 12 → 11
-- 3. arena_rounds: round_number range 1-12 → 0-10
-- 4. arena_rounds: round_type RISK_PROFILE, SECTOR, ASSET → PRE_ROUND, ASSET

-- Drop existing constraints
ALTER TABLE arena_sessions
    DROP CONSTRAINT IF EXISTS arena_sessions_current_round_check;

ALTER TABLE arena_sessions
    ALTER COLUMN total_rounds SET DEFAULT 11;

ALTER TABLE arena_rounds
    DROP CONSTRAINT IF EXISTS arena_rounds_round_number_check;

ALTER TABLE arena_rounds
    DROP CONSTRAINT IF EXISTS arena_rounds_round_type_check;

-- Add updated constraints
ALTER TABLE arena_sessions
    ADD CONSTRAINT arena_sessions_current_round_check
    CHECK (current_round >= 0 AND current_round <= 10);

ALTER TABLE arena_rounds
    ADD CONSTRAINT arena_rounds_round_number_check
    CHECK (round_number >= 0 AND round_number <= 10);

ALTER TABLE arena_rounds
    ADD CONSTRAINT arena_rounds_round_type_check
    CHECK (round_type IN ('PRE_ROUND', 'ASSET'));

-- Update existing data (if any)
-- Update sessions that are at round 1-2 to be at round 0 (Pre Round not completed yet)
UPDATE arena_sessions
SET current_round = 0, total_rounds = 11
WHERE current_round <= 2 AND status = 'IN_PROGRESS';

-- Update sessions that are at round 3+ to adjusted round numbers (subtract 2)
UPDATE arena_sessions
SET current_round = current_round - 2, total_rounds = 11
WHERE current_round > 2;

-- Update round types
UPDATE arena_rounds
SET round_type = 'PRE_ROUND'
WHERE round_type IN ('RISK_PROFILE', 'SECTOR');

-- Renumber rounds: old round 1-2 → round 0, old round 3-12 → round 1-10
UPDATE arena_rounds
SET round_number = 0
WHERE round_number <= 2 AND round_type = 'PRE_ROUND';

UPDATE arena_rounds
SET round_number = round_number - 2
WHERE round_number > 2 AND round_type = 'ASSET';