-- Прогресс по курсам. Контент курсов лежит в git (classpath:courses/), поэтому здесь только slug курса и id задачи.

-- Каждая проверка решения задачи курса.
CREATE TABLE course_task_attempt (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    course_slug  VARCHAR(100) NOT NULL,
    task_id      VARCHAR(100) NOT NULL,
    code         TEXT         NOT NULL,
    passed_tests INT          NOT NULL,
    total_tests  INT          NOT NULL,
    solved       BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX course_task_attempt_user_idx ON course_task_attempt (user_id, task_id, created_at);

-- Итог по задаче для ученика: обновляется при каждой проверке.
-- solved, однажды ставший TRUE, больше не сбрасывается; last_code — код последней проверки.
CREATE TABLE course_task_status (
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    course_slug VARCHAR(100) NOT NULL,
    task_id     VARCHAR(100) NOT NULL,
    solved      BOOLEAN      NOT NULL,
    last_code   TEXT         NOT NULL,
    solved_at   TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, task_id)
);

CREATE INDEX course_task_status_course_idx ON course_task_status (course_slug, user_id);
