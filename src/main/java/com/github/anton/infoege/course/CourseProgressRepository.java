package com.github.anton.infoege.course;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Попытки и статусы задач курсов. Через JdbcTemplate, потому что статус обновляется одним upsert
 * (INSERT ... ON CONFLICT), а сущности для двух маленьких таблиц не нужны.
 */
@Repository
public class CourseProgressRepository {

    /** Статус задачи ученика. */
    public record TaskStatus(String taskId, boolean solved, String lastCode) {
    }

    private final JdbcTemplate jdbc;

    public CourseProgressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAttempt(long userId, String courseSlug, String taskId, String code, int passed, int total,
                            boolean solved, Instant at) {
        jdbc.update("""
                INSERT INTO course_task_attempt (user_id, course_slug, task_id, code, passed_tests, total_tests, solved, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                userId, courseSlug, taskId, code, passed, total, solved, Timestamp.from(at));
    }

    /** Обновляет статус после проверки. Возвращает, решена ли задача теперь (с учётом прошлых проверок). */
    public boolean upsertStatus(long userId, String courseSlug, String taskId, String code, boolean solved, Instant at) {
        Timestamp now = Timestamp.from(at);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                INSERT INTO course_task_status (user_id, course_slug, task_id, solved, last_code, solved_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, task_id) DO UPDATE SET
                    solved     = course_task_status.solved OR EXCLUDED.solved,
                    last_code  = EXCLUDED.last_code,
                    solved_at  = COALESCE(course_task_status.solved_at, EXCLUDED.solved_at),
                    updated_at = EXCLUDED.updated_at
                RETURNING solved""",
                Boolean.class, userId, courseSlug, taskId, solved, code, solved ? now : null, now));
    }

    /** Статусы задач курса у ученика: task_id → статус. */
    public Map<String, TaskStatus> statuses(long userId, String courseSlug) {
        Map<String, TaskStatus> result = new HashMap<>();
        jdbc.query("SELECT task_id, solved, last_code FROM course_task_status WHERE user_id = ? AND course_slug = ?",
                rs -> {
                    result.put(rs.getString("task_id"),
                            new TaskStatus(rs.getString("task_id"), rs.getBoolean("solved"), rs.getString("last_code")));
                }, userId, courseSlug);
        return result;
    }
}
