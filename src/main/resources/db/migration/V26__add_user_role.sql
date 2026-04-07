-- Add role column to users table for admin functionality
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Add check constraint for valid role values
ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'));

-- Add index for role queries
CREATE INDEX idx_users_role ON users(role);