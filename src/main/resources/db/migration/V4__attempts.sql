CREATE TABLE attempt (
    id                BIGSERIAL PRIMARY KEY,
    assignment_id     BIGINT      NOT NULL UNIQUE REFERENCES assignment (id),
    started_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    status            VARCHAR(16) NOT NULL CHECK (status IN ('IN_PROGRESS', 'FINISHED', 'EXPIRED')),
    score             INT,
    last_heartbeat_at TIMESTAMPTZ
);

CREATE INDEX attempt_in_progress_idx ON attempt (expires_at) WHERE status = 'IN_PROGRESS';

CREATE TABLE attempt_answer (
    attempt_id         BIGINT      NOT NULL REFERENCES attempt (id),
    task_id            BIGINT      NOT NULL REFERENCES task (id),
    position           INT         NOT NULL,
    answer             JSONB,
    answer_snapshot    JSONB       NOT NULL,
    points_snapshot    INT         NOT NULL,
    scoring_snapshot   VARCHAR(16) NOT NULL CHECK (scoring_snapshot IN ('ALL_OR_NOTHING', 'PER_ROW')),
    is_correct         BOOLEAN,
    points             INT,
    time_spent_seconds INT         NOT NULL DEFAULT 0,
    answer_changes     INT         NOT NULL DEFAULT 0,
    first_opened_at    TIMESTAMPTZ,
    last_saved_at      TIMESTAMPTZ,
    PRIMARY KEY (attempt_id, task_id)
);

CREATE INDEX attempt_answer_task_idx ON attempt_answer (task_id);
