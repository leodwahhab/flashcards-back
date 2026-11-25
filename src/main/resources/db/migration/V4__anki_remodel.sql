-- Remodelagem estilo Anki (ver docs/arquitetura.md, secao 3).
-- Independente da V3 local (insert_admin): usuarios criados por ela tambem recebem os padroes.

-- ---------------------------------------------------------------------------------------------
-- "user": timestamptz (os valores legados eram gravados em UTC) e e-mail normalizado
-- ---------------------------------------------------------------------------------------------
ALTER TABLE "user"
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE "user"
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE "user"
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW();
UPDATE "user" SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

-- ---------------------------------------------------------------------------------------------
-- Novas tabelas
-- ---------------------------------------------------------------------------------------------
CREATE TABLE deck_options (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT       NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    is_default             BOOLEAN      NOT NULL DEFAULT FALSE,
    new_per_day            INTEGER      NOT NULL DEFAULT 20,
    reviews_per_day        INTEGER      NOT NULL DEFAULT 200,
    learning_steps         VARCHAR(100) NOT NULL DEFAULT '1 10',
    relearning_steps       VARCHAR(100) NOT NULL DEFAULT '10',
    graduating_interval_days INTEGER    NOT NULL DEFAULT 1,
    easy_interval_days     INTEGER      NOT NULL DEFAULT 4,
    starting_ease          INTEGER      NOT NULL DEFAULT 2500,
    easy_bonus             INTEGER      NOT NULL DEFAULT 130,
    hard_interval          INTEGER      NOT NULL DEFAULT 120,
    interval_modifier      INTEGER      NOT NULL DEFAULT 100,
    lapse_new_interval     INTEGER      NOT NULL DEFAULT 0,
    minimum_interval       INTEGER      NOT NULL DEFAULT 1,
    maximum_interval       INTEGER      NOT NULL DEFAULT 36500,
    leech_threshold        INTEGER      NOT NULL DEFAULT 8,
    bury_siblings          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uk_deck_options_user_name ON deck_options (user_id, lower(name));

CREATE TABLE deck (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    parent_id       BIGINT       REFERENCES deck (id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    options_id      BIGINT       NOT NULL REFERENCES deck_options (id) ON DELETE RESTRICT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    legacy_group_id BIGINT -- temporaria: mapeamento flashcard_group -> deck
);
CREATE UNIQUE INDEX uk_deck_siblings_name ON deck (user_id, COALESCE(parent_id, 0), lower(name));
CREATE INDEX idx_deck_user_id ON deck (user_id);
CREATE INDEX idx_deck_options_id ON deck (options_id);

CREATE TABLE note (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT        NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    note_type            VARCHAR(20)   NOT NULL,
    front                TEXT          NOT NULL DEFAULT '',
    back                 TEXT          NOT NULL DEFAULT '',
    image_url            VARCHAR(2048),
    guid                 VARCHAR(36)   NOT NULL,
    dup_key              VARCHAR(64)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    legacy_flashcard_id  BIGINT, -- temporaria: mapeamento flashcard -> note
    CONSTRAINT uk_note_guid UNIQUE (guid),
    CONSTRAINT chk_note_type CHECK (note_type IN ('BASIC', 'BASIC_REVERSED', 'CLOZE', 'TYPE_IN')),
    CONSTRAINT chk_note_content CHECK (front <> '' OR image_url IS NOT NULL)
);
CREATE INDEX idx_note_user_type_dup ON note (user_id, note_type, dup_key);

CREATE TABLE tag (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    name    VARCHAR(100) NOT NULL,
    CONSTRAINT uk_tag_user_name UNIQUE (user_id, name)
);

CREATE TABLE note_tag (
    note_id BIGINT NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);
CREATE INDEX idx_note_tag_tag_id ON note_tag (tag_id);

CREATE TABLE card (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    note_id          BIGINT      NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    deck_id          BIGINT      NOT NULL REFERENCES deck (id) ON DELETE CASCADE,
    ordinal          INTEGER     NOT NULL DEFAULT 0,
    state            VARCHAR(12) NOT NULL DEFAULT 'NEW',
    position         BIGINT      NOT NULL DEFAULT 0,
    due_at           TIMESTAMPTZ NOT NULL,
    interval_days    INTEGER     NOT NULL DEFAULT 0,
    ease_permille    INTEGER     NOT NULL DEFAULT 2500,
    reps             INTEGER     NOT NULL DEFAULT 0,
    lapses           INTEGER     NOT NULL DEFAULT 0,
    learning_step    INTEGER     NOT NULL DEFAULT 0,
    last_review_at   TIMESTAMPTZ,
    suspended        BOOLEAN     NOT NULL DEFAULT FALSE,
    buried_until     TIMESTAMPTZ,
    flag             SMALLINT    NOT NULL DEFAULT 0,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_card_note_ordinal UNIQUE (note_id, ordinal),
    CONSTRAINT chk_card_state CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT chk_card_flag CHECK (flag BETWEEN 0 AND 7)
);
CREATE INDEX idx_card_queue ON card (user_id, deck_id, state, due_at);
CREATE INDEX idx_card_note_id ON card (note_id);
CREATE INDEX idx_card_deck_id ON card (deck_id);

-- card_id e deck_id sem FK: o historico sobrevive a exclusao do card/baralho
CREATE TABLE review_log (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
    card_id              BIGINT      NOT NULL,
    deck_id              BIGINT,
    rating               SMALLINT    NOT NULL,
    state_before         VARCHAR(12) NOT NULL,
    state_after          VARCHAR(12) NOT NULL,
    interval_before      INTEGER     NOT NULL,
    interval_after       INTEGER     NOT NULL,
    ease_before          INTEGER     NOT NULL,
    ease_after           INTEGER     NOT NULL,
    due_before           TIMESTAMPTZ NOT NULL,
    due_after            TIMESTAMPTZ NOT NULL,
    reps_before          INTEGER     NOT NULL,
    lapses_before        INTEGER     NOT NULL,
    lapses_after         INTEGER     NOT NULL,
    learning_step_before INTEGER     NOT NULL,
    last_review_before   TIMESTAMPTZ,
    time_ms              INTEGER     NOT NULL DEFAULT 0,
    reviewed_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 4)
);
CREATE INDEX idx_review_log_user_reviewed ON review_log (user_id, reviewed_at);
CREATE INDEX idx_review_log_card_id ON review_log (card_id);

-- ---------------------------------------------------------------------------------------------
-- Migracao dos dados legados
-- ---------------------------------------------------------------------------------------------

-- 1. Opcoes padrao e baralho "Padrao" para todo usuario
INSERT INTO deck_options (user_id, name, is_default)
SELECT id, 'Padrão', TRUE FROM "user";

INSERT INTO deck (user_id, parent_id, name, options_id)
SELECT u.id, NULL, 'Padrão', o.id
FROM "user" u
         JOIN deck_options o ON o.user_id = u.id AND o.is_default;

-- 2. Cada grupo vira um baralho raiz. Nomes repetidos (sem diferenciar maiusculas) ou iguais a "Padrao"
--    recebem sufixo " (n)" para respeitar o indice unico de irmaos.
INSERT INTO deck (user_id, parent_id, name, options_id, legacy_group_id)
SELECT g.user_id,
       NULL,
       CASE WHEN g.rn = 1 THEN g.base_name ELSE left(g.base_name, 240) || ' (' || g.rn || ')' END,
       o.id,
       g.id
FROM (SELECT fg.id,
             fg.user_id,
             COALESCE(NULLIF(btrim(fg.name), ''), 'Grupo') AS base_name,
             row_number() OVER (PARTITION BY fg.user_id, lower(COALESCE(NULLIF(btrim(fg.name), ''), 'Grupo'))
                 ORDER BY fg.id)
                 + CASE WHEN lower(COALESCE(NULLIF(btrim(fg.name), ''), 'Grupo')) = lower('Padrão') THEN 1 ELSE 0 END AS rn
      FROM flashcard_group fg) g
         JOIN deck_options o ON o.user_id = g.user_id AND o.is_default;

-- 3. Cada flashcard vira um note BASIC
INSERT INTO note (user_id, note_type, front, back, image_url, guid, dup_key, created_at, updated_at, legacy_flashcard_id)
SELECT f.user_id,
       'BASIC',
       x.front,
       f.answer,
       f.image_url,
       gen_random_uuid()::text,
       -- mesma normalizacao de Texts.dupKey: sem HTML, minusculas, espacos colapsados, SHA-256 hex
       encode(sha256(convert_to(
               btrim(regexp_replace(lower(regexp_replace(x.front, '<[^>]*>', '', 'g')), '\s+', ' ', 'g')),
               'UTF8')), 'hex'),
       f.created_at AT TIME ZONE 'UTC',
       f.updated_at AT TIME ZONE 'UTC',
       f.id
FROM flashcard f
         CROSS JOIN LATERAL (
    SELECT CASE
               WHEN btrim(COALESCE(f.text_content, '')) = '' AND f.image_url IS NULL THEN '(sem conteúdo)'
               ELSE COALESCE(f.text_content, '')
               END AS front
    ) x;

-- 4. Grupo de menor id vira o baralho do card; os demais viram tags "grupo-<nome>"
CREATE TEMPORARY TABLE tmp_primary_group AS
SELECT a.flashcard_id, min(a.group_id) AS group_id
FROM flashcard_group_assignment a
GROUP BY a.flashcard_id;

CREATE TEMPORARY TABLE tmp_extra_tag AS
SELECT DISTINCT f.user_id,
                f.id AS flashcard_id,
                left('grupo-' || regexp_replace(lower(btrim(g.name)), '\s+', '-', 'g'), 100) AS tag_name
FROM flashcard_group_assignment a
         JOIN tmp_primary_group p ON p.flashcard_id = a.flashcard_id AND a.group_id <> p.group_id
         JOIN flashcard f ON f.id = a.flashcard_id
         JOIN flashcard_group g ON g.id = a.group_id;

INSERT INTO card (user_id, note_id, deck_id, ordinal, state, position, due_at, interval_days, ease_permille,
                  reps, lapses, learning_step, last_review_at, created_at, updated_at)
SELECT n.user_id,
       n.id,
       COALESCE(d.id, dd.id),
       0,
       CASE WHEN f.last_shown_date IS NULL THEN 'NEW' ELSE 'REVIEW' END,
       f.id,
       NOW(),
       CASE
           WHEN f.last_shown_date IS NULL THEN 0
           WHEN f.dominance_level = 'high' THEN 21
           WHEN f.dominance_level = 'medium' THEN 6
           ELSE 1
           END,
       2500,
       f.attempts_correct + f.attempts_incorrect,
       0,
       0,
       f.last_shown_date AT TIME ZONE 'UTC',
       f.created_at AT TIME ZONE 'UTC',
       f.updated_at AT TIME ZONE 'UTC'
FROM note n
         JOIN flashcard f ON f.id = n.legacy_flashcard_id
         LEFT JOIN tmp_primary_group p ON p.flashcard_id = f.id
         LEFT JOIN deck d ON d.legacy_group_id = p.group_id AND d.user_id = f.user_id
         JOIN deck dd ON dd.user_id = n.user_id AND dd.parent_id IS NULL AND dd.legacy_group_id IS NULL;

INSERT INTO tag (user_id, name)
SELECT DISTINCT user_id, tag_name FROM tmp_extra_tag;

INSERT INTO note_tag (note_id, tag_id)
SELECT DISTINCT n.id, t.id
FROM tmp_extra_tag e
         JOIN note n ON n.legacy_flashcard_id = e.flashcard_id
         JOIN tag t ON t.user_id = e.user_id AND t.name = e.tag_name;

DROP TABLE tmp_extra_tag;
DROP TABLE tmp_primary_group;

-- 5. Remocao do legado e das colunas temporarias
DROP TABLE flashcard_group_assignment;
DROP TABLE flashcard_group;
DROP TABLE flashcard;
DROP TABLE study_session;

ALTER TABLE note DROP COLUMN legacy_flashcard_id;
ALTER TABLE deck DROP COLUMN legacy_group_id;
