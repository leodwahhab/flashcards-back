CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE flashcards (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    text_content TEXT,
    image_url VARCHAR(2048),
    answer TEXT NOT NULL,
    dominance_level VARCHAR(10) NOT NULL DEFAULT 'low',
    last_shown_date TIMESTAMP,
    attempts_correct INTEGER NOT NULL DEFAULT 0,
    attempts_incorrect INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_content CHECK (text_content IS NOT NULL OR image_url IS NOT NULL)
);

CREATE TABLE flashcard_groups (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE flashcard_group_assignments (
    flashcard_id BIGINT NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES flashcard_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (flashcard_id, group_id)
);

CREATE INDEX idx_flashcards_user_id ON flashcards(user_id);
CREATE INDEX idx_flashcard_groups_user_id ON flashcard_groups(user_id);
