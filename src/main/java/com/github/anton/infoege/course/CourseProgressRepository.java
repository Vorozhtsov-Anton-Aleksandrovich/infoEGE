package com.github.anton.infoege.course;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** Задача, которую ученик проверял последней в курсе, — для кнопки «Продолжить». */
    public Optional<String> lastTaskId(long userId, String courseSlug) {
        return jdbc.query("""
                SELECT task_id FROM course_task_status WHERE user_id = ? AND course_slug = ?
                ORDER BY updated_at DESC LIMIT 1""",
                (rs, i) -> rs.getString(1), userId, courseSlug).stream().findFirst();
    }

    /** Решённые задачи курса у всех учеников: user_id → множество task_id. */
    public Map<Long, Set<String>> solvedByUser(String courseSlug) {
        Map<Long, Set<String>> result = new HashMap<>();
        jdbc.query("SELECT user_id, task_id FROM course_task_status WHERE course_slug = ? AND solved",
                rs -> {
                    result.computeIfAbsent(rs.getLong(1), id -> new HashSet<>()).add(rs.getString(2));
                }, courseSlug);
        return result;
    }

    /** Сколько раз ученик проверял каждую задачу курса: task_id → число проверок. */
    public Map<String, Integer> attemptCounts(long userId, String courseSlug) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("""
                SELECT task_id, COUNT(*) FROM course_task_attempt WHERE user_id = ? AND course_slug = ?
                GROUP BY task_id""",
                rs -> {
                    result.put(rs.getString(1), rs.getInt(2));
                }, userId, courseSlug);
        return result;
    }

    /** Проверка задачи курса: какая задача и когда — для карты активности. */
    public record AttemptTime(String taskId, Instant at) {
    }

    /** Все проверки задач курсов у ученика начиная с момента from (любые курсы). */
    public java.util.List<AttemptTime> attemptTimes(long userId, Instant from) {
        return jdbc.query("SELECT task_id, created_at FROM course_task_attempt WHERE user_id = ? AND created_at >= ?",
                (rs, i) -> new AttemptTime(rs.getString(1), rs.getTimestamp(2).toInstant()),
                userId, Timestamp.from(from));
    }

    /** Решена ли задача учеником. */
    public boolean isSolved(long userId, String taskId) {
        return Boolean.TRUE.equals(jdbc.query(
                "SELECT solved FROM course_task_status WHERE user_id = ? AND task_id = ?",
                rs -> rs.next() && rs.getBoolean(1), userId, taskId));
    }

    /** Сколько неудачных проверок у ученика по задаче. */
    public int failCount(long userId, String taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_task_attempt WHERE user_id = ? AND task_id = ? AND NOT solved",
                Integer.class, userId, taskId);
        return count == null ? 0 : count;
    }

    /** Неудачные проверки по задачам курса: task_id → число. Нужны, чтобы решить, открыт ли разбор. */
    public Map<String, Integer> failCounts(long userId, String courseSlug) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("""
                SELECT task_id, COUNT(*) FROM course_task_attempt WHERE user_id = ? AND course_slug = ? AND NOT solved
                GROUP BY task_id""",
                rs -> {
                    result.put(rs.getString(1), rs.getInt(2));
                }, userId, courseSlug);
        return result;
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
