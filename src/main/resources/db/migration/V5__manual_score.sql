ALTER TABLE attempt
    ADD COLUMN manual_score      INT CHECK (manual_score >= 0),
    ADD COLUMN manual_comment    TEXT,
    ADD COLUMN manual_updated_at TIMESTAMPTZ;
