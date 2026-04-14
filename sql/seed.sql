-- Optional seed data for local development
-- Run after schema.sql if you want sample records.

INSERT INTO users (email, password, username, created_at)
VALUES
    ('host@example.com', '$2a$10$examplehashedpassword', 'hostuser', EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (email) DO NOTHING;

INSERT INTO rooms (room_id, host_id, room_name, created_at, is_active)
SELECT 'sample-room-001', id, 'Sample Room', EXTRACT(EPOCH FROM NOW()) * 1000, TRUE
FROM users
WHERE email = 'host@example.com'
ON CONFLICT (room_id) DO NOTHING;
