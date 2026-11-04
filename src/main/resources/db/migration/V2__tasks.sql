CREATE TABLE task (
    id             BIGSERIAL PRIMARY KEY,
    ege_number     INT          NOT NULL CHECK (ege_number >= 1),
    title          VARCHAR(300) NOT NULL,
    statement_md   TEXT         NOT NULL,
    difficulty     INT          NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    max_points     INT          NOT NULL CHECK (max_points >= 0),
    scoring        VARCHAR(16)  NOT NULL CHECK (scoring IN ('ALL_OR_NOTHING', 'PER_ROW')),
    answer_rows    INT          NOT NULL CHECK (answer_rows >= 1),
    answer_cols    INT          NOT NULL CHECK (answer_cols >= 1),
    correct_answer JSONB        NOT NULL,
    solution_md    TEXT,
    topic          VARCHAR(200),
    archived       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX task_ege_number_idx ON task (ege_number);

CREATE TABLE task_file (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES task (id),
    original_name VARCHAR(255) NOT NULL,
    storage_key   VARCHAR(100) NOT NULL UNIQUE,
    size_bytes    BIGINT       NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    kind          VARCHAR(16)  NOT NULL CHECK (kind IN ('ATTACHMENT', 'IMAGE')),
    UNIQUE (task_id, original_name)
);
