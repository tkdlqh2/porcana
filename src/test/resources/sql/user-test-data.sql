-- Test user data for UserControllerTest
-- Password is stored as plain text because passwordEncoder is mocked
DELETE FROM users WHERE email = 'test@example.com';

INSERT INTO users (id, email, password, nickname, provider, main_portfolio_id, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'test@example.com', 'password123', '테스터', 'EMAIL', NULL, NOW(), NOW());