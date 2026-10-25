-- ==============================================================================
-- Smart English Notes - PostgreSQL Database Schema
-- Version: v2.1.0 (Single-Contract Standard)
-- Target UX/UI: margin.
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- 2. Topics Table (Notebook Hierarchy)
CREATE TABLE IF NOT EXISTS topics (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_topics_user ON topics(user_id) WHERE deleted_at IS NULL;

-- 3. Notes Table (Notebook Editor)
CREATE TABLE IF NOT EXISTS notes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID REFERENCES topics(id) ON DELETE SET NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_notes_user_topic ON notes(user_id, topic_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(user_id, updated_at DESC);

-- 4. User Words Table (SRS Core)
-- WITH fillfactor = 80 for PostgreSQL Heap-Only Tuples (HOT) updates on frequent review updates
CREATE TABLE IF NOT EXISTS user_words (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text VARCHAR(255) NOT NULL,
    text_normalized VARCHAR(255) NOT NULL,
    primary_meaning TEXT,
    ipa VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    correct_streak INT NOT NULL DEFAULT 0,
    ease_factor DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days INT NOT NULL DEFAULT 0,
    next_review_at TIMESTAMP WITH TIME ZONE,
    failed_essay_streak INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
) WITH (fillfactor = 80);

CREATE INDEX IF NOT EXISTS idx_user_words_due ON user_words(user_id, next_review_at ASC, status ASC) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_words_lookup ON user_words(user_id, text_normalized) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_user_words_updated ON user_words(user_id, updated_at DESC);

-- 5. Word Contexts Table (Multi-Context Harvest)
CREATE TABLE IF NOT EXISTS word_contexts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_word_id UUID NOT NULL REFERENCES user_words(id) ON DELETE CASCADE,
    note_id UUID REFERENCES notes(id) ON DELETE SET NULL,
    note_title_snapshot VARCHAR(500),
    part_of_speech VARCHAR(50),
    meaning TEXT NOT NULL,
    sentence TEXT,
    sentence_translation TEXT,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_word_contexts_word ON word_contexts(user_id, user_word_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_word_contexts_note ON word_contexts(note_id) WHERE deleted_at IS NULL;

-- 6. Review Logs Table (Immutable Event Sourcing)
CREATE TABLE IF NOT EXISTS review_logs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_word_id UUID NOT NULL REFERENCES user_words(id) ON DELETE CASCADE,
    mode VARCHAR(50) NOT NULL,
    quality INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_review_logs_event ON review_logs(event_id);
CREATE INDEX IF NOT EXISTS idx_review_logs_user_date ON review_logs(user_id, reviewed_at DESC);

-- 7. User App Config Table (User Preferences & Timezone)
CREATE TABLE IF NOT EXISTS user_app_config (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    timezone VARCHAR(100) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    daily_target INT NOT NULL DEFAULT 20,
    sidebar_width INT NOT NULL DEFAULT 244,
    theme VARCHAR(50) NOT NULL DEFAULT 'light',
    auto_speak BOOLEAN NOT NULL DEFAULT false,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. User Sync Metadata Table (Counters & Streak)
CREATE TABLE IF NOT EXISTS user_sync_metadata (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    client_version VARCHAR(50),
    new_count INT NOT NULL DEFAULT 0,
    learning_count INT NOT NULL DEFAULT 0,
    remembered_count INT NOT NULL DEFAULT 0,
    mastered_count INT NOT NULL DEFAULT 0,
    current_streak_days INT NOT NULL DEFAULT 0,
    longest_streak_days INT NOT NULL DEFAULT 0,
    last_active_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- 9. Daily Stats Table (35-Day Bento Activity Calendar)
CREATE TABLE IF NOT EXISTS daily_stats (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_date DATE NOT NULL,
    words_reviewed INT NOT NULL DEFAULT 0,
    words_added INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    time_spent_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_daily_stats_user_date UNIQUE (user_id, study_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_stats_user_date ON daily_stats(user_id, study_date DESC);

-- 10. AI Chat Conversations & Messages
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversations_user ON conversations(user_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(100),
    tokens_used INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conv ON chat_messages(conversation_id, created_at ASC);

-- 11. User AI Settings Table
CREATE TABLE IF NOT EXISTS user_ai_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL DEFAULT '9router',
    api_key_encrypted TEXT,
    custom_base_url VARCHAR(500),
    model VARCHAR(100) NOT NULL DEFAULT 'gpt-4o-mini',
    vision_model VARCHAR(100),
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 12. Default Initial Seed Users
INSERT INTO users (id, username, name, email, password, role, version, created_at, updated_at)
VALUES
    ('019273c0-0001-7000-8000-000000000001', 'admin', 'Admin User', 'admin@learningenglish.com', '$2a$10$tg2DV6VQsJ651DgRC.n5vOWVBV7//8hRTzYLlcaYhUxrwMfGgNZEu', 'ROLE_ADMIN', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0002-7000-8000-000000000002', 'User1', 'User 1', 'user1@learningenglish.com', '$2a$10$pOsgt/EbVo1zIt34v9IJUeGUf4DsSjak15aZFG.KWa/f5PJqYJkEy', 'ROLE_USER', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0003-7000-8000-000000000003', 'user2', 'User 2', 'user2@learningenglish.com', '$2a$10$z0U4YaoGQGbcBnbA8WibFuMamf/TNrntt/hulERDbR3TejzbDNW0y', 'ROLE_USER', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_app_config (id, user_id, timezone, daily_target, sidebar_width, theme, auto_speak, version, created_at, updated_at)
VALUES
    ('019273c0-0001-7000-8000-000000000011', '019273c0-0001-7000-8000-000000000001', 'Asia/Ho_Chi_Minh', 20, 244, 'light', false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0002-7000-8000-000000000012', '019273c0-0002-7000-8000-000000000002', 'Asia/Ho_Chi_Minh', 20, 244, 'light', false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0003-7000-8000-000000000013', '019273c0-0003-7000-8000-000000000003', 'Asia/Ho_Chi_Minh', 20, 244, 'light', false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_sync_metadata (id, user_id, new_count, learning_count, remembered_count, mastered_count, current_streak_days, longest_streak_days, version, created_at, updated_at)
VALUES
    ('019273c0-0001-7000-8000-000000000021', '019273c0-0001-7000-8000-000000000001', 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0002-7000-8000-000000000022', '019273c0-0002-7000-8000-000000000002', 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('019273c0-0003-7000-8000-000000000023', '019273c0-0003-7000-8000-000000000003', 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

