package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.course.Course.CourseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка решения задачи курса. Код ученика выполняется в его браузере (Pyodide), сюда приходят только
 * выводы программы на каждом тесте; сравнение с эталоном — на сервере, эталон скрытых тестов клиенту не уходит.
 */
@Service
public class CourseCheckService {

    public static final int MAX_CODE_LENGTH = 64 * 1024;
    /** Столько же ограничивает воркер в браузере, плюс запас на многобайтовые символы и маркер обрезки. */
    public static final int MAX_OUTPUT_LENGTH = 110_000;
    public static final Duration MIN_INTERVAL = Duration.ofSeconds(2);
    /** Ответ проверяется мгновенно, поэтому интервал короче, чем у кода. */
    public static final Duration MIN_ANSWER_INTERVAL = Duration.ofMillis(700);

    public static final String OK = "ok";
    public static final String ERROR = "error";
    public static final String TIMEOUT = "timeout";
    private static final Set<String> STATUSES = Set.of(OK, ERROR, TIMEOUT);

    /** Результат одного теста: status — passed / wrong / error / timeout. */
    public record TestResult(int index, boolean hidden, boolean passed, String status) {
    }

    /**
     * solved — прошла ли эта проверка; taskSolved — решена ли задача вообще (с учётом прошлых проверок);
     * solutionHtml — разбор, если он уже открыт (иначе null).
     */
    public record CheckResult(boolean solved, boolean taskSolved, int passed, int total, List<TestResult> results,
                              String solutionHtml) {
    }

    /**
     * Результат проверки ответа задачи без кода. mismatches — сколько строк таблицы или вершин неверно (-1, если
     * не считается); fails — сколько всего неудачных попыток; failsToSolution — сколько ещё ошибок до разбора;
     * solutionHtml — разбор, если он уже открыт.
     */
    public record AnswerResult(boolean correct, boolean taskSolved, int mismatches, int fails, int failsToSolution,
                               String solutionHtml) {
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException() {
            super("Слишком часто: подождите пару секунд");
        }
    }

    private final CourseProgressRepository progress;
    private final Clock clock;
    private final Map<Long, Instant> lastCheck = new ConcurrentHashMap<>();

    @Autowired
    public CourseCheckService(CourseProgressRepository progress) {
        this(progress, Clock.systemUTC());
    }

    CourseCheckService(CourseProgressRepository progress, Clock clock) {
        this.progress = progress;
        this.clock = clock;
    }

    @Transactional
    public CheckResult check(long userId, String courseSlug, CourseTask task, String code,
                             List<String> outputs, List<String> statuses) {
        validate(task, code, outputs, statuses);
        Instant now = clock.instant();
        throttle(userId, now, MIN_INTERVAL);

        List<CourseTest> tests = task.tests();
        List<TestResult> results = new ArrayList<>(tests.size());
        int passed = 0;
        for (int i = 0; i < tests.size(); i++) {
            CourseTest test = tests.get(i);
            String status = statuses.get(i);
            boolean ok = OK.equals(status) && OutputComparison.matches(outputs.get(i), test.output());
            if (ok) {
                passed++;
            }
            results.add(new TestResult(i, test.hidden(), ok, ok ? "passed" : OK.equals(status) ? "wrong" : status));
        }
        boolean solved = passed == tests.size();
        progress.saveAttempt(userId, courseSlug, task.id(), code, passed, tests.size(), solved, now);
        boolean taskSolved = progress.upsertStatus(userId, courseSlug, task.id(), code, solved, now);
        int fails = progress.failCount(userId, task.id());
        return new CheckResult(solved, taskSolved, passed, tests.size(), results,
                task.solutionOpen(taskSolved, fails) ? task.solutionHtml() : null);
    }

    /** Разбор задачи, если он уже открыт ученику (решил или ошибся достаточно раз), иначе пусто. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> solution(long userId, CourseTask task) {
        boolean open = task.solutionOpen(progress.isSolved(userId, task.id()), progress.failCount(userId, task.id()));
        return open ? java.util.Optional.of(task.solutionHtml()) : java.util.Optional.empty();
    }

    /** Проверка ответа задачи без кода. Ответ сохраняется как «последний код», чтобы вернуть его на страницу. */
    @Transactional
    public AnswerResult checkAnswer(long userId, String courseSlug, CourseTask task, String answer) {
        if (task.isCode()) {
            throw new IllegalArgumentException("Эта задача проверяется запуском программы");
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("Введите ответ");
        }
        if (answer.length() > CourseAnswers.MAX_LENGTH) {
            throw new IllegalArgumentException("Слишком длинный ответ");
        }
        Instant now = clock.instant();
        throttle(userId, now, MIN_ANSWER_INTERVAL);

        String normalized = CourseAnswers.normalize(task.type(), task.answerFormat(), answer);
        boolean correct = CourseAnswers.matches(task.type(), task.answerFormat(), task.answers(), answer);
        int mismatches = correct ? 0 : CourseAnswers.mismatches(task.type(), normalized, task.answers().get(0));
        String saved = answer.strip();
        progress.saveAttempt(userId, courseSlug, task.id(), saved, correct ? 1 : 0, 1, correct, now);
        boolean taskSolved = progress.upsertStatus(userId, courseSlug, task.id(), saved, correct, now);
        int fails = progress.failCount(userId, task.id());
        return new AnswerResult(correct, taskSolved, mismatches, fails,
                Math.max(0, CourseTask.SOLUTION_AFTER_FAILS - fails),
                task.solutionOpen(taskSolved, fails) ? task.solutionHtml() : null);
    }

    private static void validate(CourseTask task, String code, List<String> outputs, List<String> statuses) {
        int count = task.tests().size();
        if (code == null || code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Код не должен быть длиннее 64 КБ");
        }
        if (outputs == null || statuses == null || outputs.size() != count || statuses.size() != count) {
            throw new IllegalArgumentException("Нужны результаты по всем " + count + " тестам");
        }
        for (int i = 0; i < count; i++) {
            if (!STATUSES.contains(statuses.get(i))) {
                throw new IllegalArgumentException("Неизвестный статус теста: " + statuses.get(i));
            }
            String output = outputs.get(i);
            if (output != null && output.length() > MAX_OUTPUT_LENGTH) {
                throw new IllegalArgumentException("Слишком длинный вывод в тесте " + (i + 1));
            }
        }
    }

    /** Не чаще одной проверки в interval на пользователя. Счётчики в памяти: перезапуск их сбрасывает. */
    private void throttle(long userId, Instant now, Duration interval) {
        boolean[] allowed = {false};
        lastCheck.compute(userId, (id, last) -> {
            if (last == null || !now.isBefore(last.plus(interval))) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        if (!allowed[0]) {
            throw new TooManyRequestsException();
        }
        if (lastCheck.size() > 10_000) {
            lastCheck.values().removeIf(t -> t.isBefore(now.minus(MIN_INTERVAL)));
        }
    }
}
