-- Admin API Performance Indexes
-- Purpose: Optimize admin search and list queries with case-insensitive CONTAINS search

-- ========================================
-- Enable pg_trgm Extension for LIKE '%keyword%' Support
-- ========================================
-- pg_trgm provides GIN indexes that support contains patterns (%keyword%)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ========================================
-- User Search Indexes (GIN for contains search)
-- ========================================

-- GIN trigram index for case-insensitive email contains search
CREATE INDEX idx_users_email_trgm ON users USING GIN (LOWER(email) gin_trgm_ops);

-- GIN trigram index for case-insensitive nickname contains search
CREATE INDEX idx_users_nickname_trgm ON users USING GIN (LOWER(nickname) gin_trgm_ops);

-- Composite index for role filter with deleted_at (replace existing role index)
DROP INDEX IF EXISTS idx_users_role;
CREATE INDEX idx_users_role_deleted ON users(role, deleted_at);

-- ========================================
-- Portfolio Search Indexes (GIN for contains search)
-- ========================================

-- GIN trigram index for case-insensitive portfolio name contains search
CREATE INDEX idx_portfolios_name_trgm ON portfolios USING GIN (LOWER(name) gin_trgm_ops);

-- ========================================
-- Asset Search Indexes (GIN for contains search)
-- ========================================

-- GIN trigram indexes for case-insensitive asset search (admin API)
CREATE INDEX idx_assets_symbol_trgm ON assets USING GIN (LOWER(symbol) gin_trgm_ops);
CREATE INDEX idx_assets_name_trgm ON assets USING GIN (LOWER(name) gin_trgm_ops);

-- ========================================
-- Notes
-- ========================================
-- pg_trgm GIN indexes support:
--   - LIKE '%keyword%' (contains)
--   - LIKE 'keyword%' (prefix)
--   - ILIKE patterns (case-insensitive)
--   - Similarity searches
--
-- Trade-offs:
--   - Slower write operations (index updates)
--   - Larger index size
--   - Faster contains searches (10-100x improvement)
