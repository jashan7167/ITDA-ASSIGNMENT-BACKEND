-- PostgreSQL schema for the ITDA video conference application
-- Run after connecting to the video_conference database.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    created_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);

CREATE TABLE IF NOT EXISTS rooms (
    id BIGSERIAL PRIMARY KEY,
    room_id VARCHAR(64) NOT NULL UNIQUE,
    host_id BIGINT NOT NULL,
    room_name VARCHAR(255) NOT NULL,
    created_at BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_rooms_host
        FOREIGN KEY (host_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rooms_host_id ON rooms (host_id);
CREATE INDEX IF NOT EXISTS idx_rooms_room_id ON rooms (room_id);
CREATE INDEX IF NOT EXISTS idx_rooms_is_active ON rooms (is_active);
