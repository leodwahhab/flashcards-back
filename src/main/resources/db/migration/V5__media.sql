-- Metadados dos arquivos de mídia (o conteúdo fica no disco, em app.media.dir/<userId>/<stored_name>).
CREATE TABLE media (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    stored_name   VARCHAR(80)   NOT NULL,
    original_name VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_media_user ON media (user_id);
