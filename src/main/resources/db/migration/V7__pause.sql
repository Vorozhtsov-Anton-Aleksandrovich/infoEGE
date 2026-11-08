-- Пауза («Закончить потом»): разрешается при назначении. Пока попытка на паузе, время не идёт.
ALTER TABLE assignment ADD COLUMN allow_pause BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE attempt
    ADD COLUMN paused_at      TIMESTAMPTZ,
    ADD COLUMN paused_seconds BIGINT NOT NULL DEFAULT 0;
