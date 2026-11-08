-- Режим «Проверить ответ»: ученик сразу видит, верен ли ответ, и может исправлять его; в зачёт идёт последний.
ALTER TABLE assignment DROP CONSTRAINT assignment_show_results_mode_check;
ALTER TABLE assignment ADD CONSTRAINT assignment_show_results_mode_check
    CHECK (show_results_mode IN ('AFTER_FINISH', 'AFTER_DEADLINE', 'TEACHER_ONLY', 'INSTANT'));

-- Сколько раз ученик проверял ответ (повторная проверка того же ответа не считается).
ALTER TABLE attempt_answer ADD COLUMN check_count INT NOT NULL DEFAULT 0;
