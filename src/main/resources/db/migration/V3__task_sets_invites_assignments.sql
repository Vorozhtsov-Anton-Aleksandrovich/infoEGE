CREATE TABLE task_set (
    id                 BIGSERIAL PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('COLLECTION', 'VARIANT')),
    title              VARCHAR(300) NOT NULL,
    description        TEXT,
    time_limit_minutes INT CHECK (time_limit_minutes > 0),
    created_at         TIMESTAMPTZ  NOT NULL,
    CHECK (type <> 'VARIANT' OR time_limit_minutes IS NOT NULL)
);

CREATE TABLE task_set_item (
    task_set_id BIGINT NOT NULL REFERENCES task_set (id) ON DELETE CASCADE,
    task_id     BIGINT NOT NULL REFERENCES task (id),
    position    INT    NOT NULL,
    PRIMARY KEY (task_set_id, task_id)
);

CREATE INDEX task_set_item_task_idx ON task_set_item (task_id);

CREATE TABLE invite (
    id                   BIGSERIAL PRIMARY KEY,
    token_hash           VARCHAR(64)  NOT NULL UNIQUE,
    student_display_name VARCHAR(200) NOT NULL,
    user_id              BIGINT REFERENCES users (id),
    expires_at           TIMESTAMPTZ  NOT NULL,
    used_at              TIMESTAMPTZ,
    created_by           BIGINT       NOT NULL REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE TABLE assignment (
    id                BIGSERIAL PRIMARY KEY,
    student_id        BIGINT      NOT NULL REFERENCES users (id),
    task_set_id       BIGINT      NOT NULL REFERENCES task_set (id),
    assigned_at       TIMESTAMPTZ NOT NULL,
    deadline_at       TIMESTAMPTZ,
    show_results_mode VARCHAR(16) NOT NULL CHECK (show_results_mode IN ('AFTER_FINISH', 'AFTER_DEADLINE', 'TEACHER_ONLY')),
    CHECK (show_results_mode <> 'AFTER_DEADLINE' OR deadline_at IS NOT NULL)
);

CREATE INDEX assignment_student_idx ON assignment (student_id);
CREATE INDEX assignment_task_set_idx ON assignment (task_set_id);
