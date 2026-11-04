CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('TEACHER', 'STUDENT')),
    display_name  VARCHAR(200) NOT NULL,
    login         VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL
);
