package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.course.Course.Lesson;
import com.github.anton.infoege.course.CourseProgressRepository.TaskStatus;
import com.github.anton.infoege.student.StudentService;
import com.github.anton.infoege.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Прогресс по курсам: у ученика (списки курсов и уроков, главная) и у преподавателя (таблица учеников).
 */
@Service
public class CourseProgressService {

    /** «Решено solved из total». Цвет ячейки — как в аналитике: зелёный ≥ 80 %, жёлтый ≥ 40 %, иначе красный. */
    public record Progress(int solved, int total) {

        public int percent() {
            return total == 0 ? 0 : solved * 100 / total;
        }

        public boolean complete() {
            return total > 0 && solved == total;
        }

        public String level() {
            if (solved == 0) {
                return "none";
            }
            int p = percent();
            return p >= 80 ? "good" : p >= 40 ? "mid" : "low";
        }

        public String label() {
            return solved + "/" + total;
        }
    }

    /** Прогресс ученика по курсу. continueLesson — урок последней проверки (или первый, если не начинал). */
    public record CourseProgress(Course course, Progress total, Map<String, Progress> byLesson,
                                 Lesson continueLesson, boolean started) {

        public Progress lesson(String lessonSlug) {
            return byLesson.get(lessonSlug);
        }
    }

    /** Строка таблицы преподавателя: ученик и прогресс по каждому уроку. */
    public record StudentRow(User student, Progress total, Map<String, Progress> byLesson) {

        public Progress lesson(String lessonSlug) {
            return byLesson.get(lessonSlug);
        }
    }

    /** Задача в карточке ученика у преподавателя. */
    public record TaskRow(CourseTask task, TaskStatus status, int attempts) {

        public String state() {
            if (status != null && status.solved()) {
                return "solved";
            }
            return status != null ? "tried" : "new";
        }
    }

    public record LessonTasks(Lesson lesson, Progress progress, List<TaskRow> tasks) {
    }

    private final CourseRegistry registry;
    private final CourseProgressRepository repository;
    private final StudentService studentService;

    public CourseProgressService(CourseRegistry registry, CourseProgressRepository repository,
                                 StudentService studentService) {
        this.registry = registry;
        this.repository = repository;
        this.studentService = studentService;
    }

    @Transactional(readOnly = true)
    public CourseProgress forUser(long userId, Course course) {
        Map<String, TaskStatus> statuses = repository.statuses(userId, course.slug());
        Set<String> solved = statuses.values().stream().filter(TaskStatus::solved).map(TaskStatus::taskId)
                .collect(Collectors.toSet());
        Lesson continueLesson = repository.lastTaskId(userId, course.slug())
                .flatMap(course::task)
                .flatMap(t -> course.lesson(t.lessonSlug()))
                .orElse(course.lessons().get(0));
        return new CourseProgress(course, progress(course.tasks(), solved), byLesson(course, solved),
                continueLesson, !statuses.isEmpty());
    }

    /** Прогресс по всем курсам: slug курса → прогресс. */
    @Transactional(readOnly = true)
    public Map<String, CourseProgress> forUser(long userId) {
        return registry.courses().stream().collect(Collectors.toMap(Course::slug, c -> forUser(userId, c),
                (a, b) -> a, LinkedHashMap::new));
    }

    /** Таблица «ученики × уроки» для преподавателя. Только активные ученики. */
    @Transactional(readOnly = true)
    public List<StudentRow> table(Course course) {
        Map<Long, Set<String>> solved = repository.solvedByUser(course.slug());
        return studentService.active().stream()
                .map(s -> {
                    Set<String> mine = solved.getOrDefault(s.getId(), Set.of());
                    return new StudentRow(s, progress(course.tasks(), mine), byLesson(course, mine));
                })
                .toList();
    }

    /** Задачи курса у ученика по урокам — со статусом, последним кодом и числом проверок. */
    @Transactional(readOnly = true)
    public List<LessonTasks> tasksOf(long userId, Course course) {
        Map<String, TaskStatus> statuses = repository.statuses(userId, course.slug());
        Map<String, Integer> attempts = repository.attemptCounts(userId, course.slug());
        return course.lessons().stream()
                .map(l -> {
                    List<TaskRow> rows = l.tasks().stream()
                            .map(t -> new TaskRow(t, statuses.get(t.id()), attempts.getOrDefault(t.id(), 0)))
                            .toList();
                    int solvedCount = (int) rows.stream().filter(r -> r.state().equals("solved")).count();
                    return new LessonTasks(l, new Progress(solvedCount, rows.size()), rows);
                })
                .toList();
    }

    private static Map<String, Progress> byLesson(Course course, Set<String> solved) {
        Map<String, Progress> result = new LinkedHashMap<>();
        for (Lesson lesson : course.lessons()) {
            result.put(lesson.slug(), progress(lesson.tasks(), solved));
        }
        return result;
    }

    private static Progress progress(List<CourseTask> tasks, Set<String> solved) {
        int count = (int) tasks.stream().filter(t -> solved.contains(t.id())).count();
        return new Progress(count, tasks.size());
    }
}
