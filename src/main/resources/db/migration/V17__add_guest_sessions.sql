-- Add guest sessions support for non-authenticated users
-- Allows users to create portfolios without signing up

-- Create guest_sessions table
CREATE TABLE guest_sessions (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Create index for cleanup batch job
CREATE INDEX idx_guest_sessions_last_seen_at ON guest_sessions(last_seen_at);

-- Add guest_session_id to portfolios table
ALTER TABLE portfolios
    ADD COLUMN guest_session_id UUID NULL;

-- Make user_id nullable (portfolios can be owned by users OR guests)
ALTER TABLE portfolios
    ALTER COLUMN user_id DROP NOT NULL;

-- Add foreign key constraint
ALTER TABLE portfolios
    ADD CONSTRAINT fk_portfolios_guest_session
    FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id) ON DELETE CASCADE;

-- Add XOR constraint: exactly one of user_id or guest_session_id must be NOT NULL
ALTER TABLE portfolios
    ADD CONSTRAINT ck_portfolios_owner_xor
    CHECK (
        (user_id IS NULL AND guest_session_id IS NOT NULL)
        OR
        (user_id IS NOT NULL AND guest_session_id IS NULL)
    );

-- Add index for guest session queries
CREATE INDEX idx_portfolios_guest_session_id ON portfolios(guest_session_id);

-- Add guest_session_id to arena_sessions table
ALTER TABLE arena_sessions
    ADD COLUMN guest_session_id UUID NULL;

-- Make user_id nullable (arena sessions can be owned by users OR guests)
ALTER TABLE arena_sessions
    ALTER COLUMN user_id DROP NOT NULL;

-- Add foreign key constraint
ALTER TABLE arena_sessions
    ADD CONSTRAINT fk_arena_sessions_guest_session
    FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id);

-- Add XOR constraint: exactly one of user_id or guest_session_id must be NOT NULL
ALTER TABLE arena_sessions
    ADD CONSTRAINT ck_arena_sessions_owner_xor
    CHECK (
        (user_id IS NULL AND guest_session_id IS NOT NULL)
        OR
        (user_id IS NOT NULL AND guest_session_id IS NULL)
    );

-- Add index for guest session queries
CREATE INDEX idx_arena_sessions_guest_session_id ON arena_sessions(guest_session_id);