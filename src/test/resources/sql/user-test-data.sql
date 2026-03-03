DELETE FROM arena_sessions WHERE user_id = '550e8400-e29b-41d4-a716-446655440000';
DELETE FROM portfolios WHERE user_id = '550e8400-e29b-41d4-a716-446655440000';
DELETE FROM users WHERE id = '550e8400-e29b-41d4-a716-446655440000';

INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at, deleted_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'test@example.com',
    'password123',
    'tester',
    'EMAIL',
    NULL,
    NOW(),
    NOW(),
    NULL
);

INSERT INTO portfolios (id, user_id, name, status, started_at, created_at, updated_at, deleted_at)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '550e8400-e29b-41d4-a716-446655440000',
    'Test Portfolio',
    'DRAFT',
    NULL,
    NOW(),
    NOW(),
    NULL
);

INSERT INTO arena_sessions (
    id,
    portfolio_id,
    user_id,
    guest_session_id,
    status,
    current_round,
    total_rounds,
    risk_profile,
    created_at,
    updated_at,
    completed_at
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '550e8400-e29b-41d4-a716-446655440000',
    NULL,
    'IN_PROGRESS',
    1,
    11,
    NULL,
    NOW(),
    NOW(),
    NULL
);
