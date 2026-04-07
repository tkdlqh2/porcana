-- Admin API Performance Indexes
-- Purpose: Optimize admin search and list queries with case-insensitive search

-- ========================================
-- User Search Indexes
-- ========================================

-- Functional index for case-insensitive email search (filtered by deleted_at)
CREATE INDEX idx_users_email_lower ON users(LOWER(email)) WHERE deleted_at IS NULL;

-- Functional index for case-insensitive nickname search (filtered by deleted_at)
CREATE INDEX idx_users_nickname_lower ON users(LOWER(nickname)) WHERE deleted_at IS NULL;

-- Composite index for role filter with deleted_at (replace existing role index)
DROP INDEX IF EXISTS idx_users_role;
CREATE INDEX idx_users_role_deleted ON users(role, deleted_at);

-- ========================================
-- Portfolio Search Indexes
-- ========================================

-- Functional index for case-insensitive portfolio name search
CREATE INDEX idx_portfolios_name_lower ON portfolios(LOWER(name)) WHERE deleted_at IS NULL;

-- ========================================
-- Asset Search Indexes
-- ========================================

-- Functional indexes for case-insensitive asset search (admin API)
CREATE INDEX idx_assets_symbol_lower ON assets(LOWER(symbol));
CREATE INDEX idx_assets_name_lower ON assets(LOWER(name));
