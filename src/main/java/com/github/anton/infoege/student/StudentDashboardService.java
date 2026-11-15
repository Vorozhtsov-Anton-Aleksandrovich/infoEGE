package com.github.anton.infoege.student;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.attempt.Attempt;
import com.github.anton.infoege.attempt.AttemptAnswer;
import com.github.anton.infoege.attempt.AttemptAnswerRepository;
import com.github.anton.infoege.attempt.AttemptService;
import com.github.anton.infoege.attempt.ResultVisibility;
import com.github.anton.infoege.course.CourseProgressRepository;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.web.Dates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Дашборд на главной ученика: сколько заданий решено, по каким номерам ЕГЭ, правильность и активность по дням.
 * Правильность берётся только из работ, результаты которых ученику уже можно видеть (настройка showResultsMode),
 * чтобы дашборд не раскрывал то, что преподаватель скрыл.
 * В карту активности входят и задачи курсов: за день — число разных задач курсов, которые ученик проверял.
 */
@Service
public class StudentDashboardService {

    /** Сколько недель показывает карта активности (как у GitHub — год). */
    static final int WEEKS = 53;

    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLL", RU);
    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);

    private final AssignmentService assignmentService;
    private final AttemptService attemptService;
    private final AttemptAnswerRepository answerRepository;
    private final TaskService taskService;
    private final Dates dates;
    private final CourseProgressRepository courseProgress;

    public StudentDashboardService(AssignmentService assignmentService, AttemptService attemptService,
                                   AttemptAnswerRepository answerRepository, TaskService taskService, Dates dates,
                                   CourseProgressRepository courseProgress) {
        this.courseProgress = courseProgress;
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
        this.answerRepository = answerRepository;
        this.taskService = taskService;
        this.dates = dates;
    }

    /**
     * @param answered сколько заданий решено (дан ответ), во всех работах
     * @param checked  сколько из них уже проверено и видно ученику
     * @param correct  сколько из проверенных верно
     */
    public record Dashboard(int answered, int checked, int correct, List<NumberRow> byNumber,
                            List<Week> weeks, int yearTotal, int activeDays) {

        public int correctPercent() {
            return percent(correct, checked);
        }
    }

    /** Строка по номеру ЕГЭ: решено, проверено (видно ученику), верно. */
    public record NumberRow(int egeNumber, int answered, int checked, int correct) {

        public int correctPercent() {
            return percent(correct, checked);
        }
    }

    /** Неделя карты активности: семь дней с понедельника; monthLabel — подпись, если с этой недели начинается месяц. */
    public record Week(String monthLabel, List<Day> days) {
    }

    /**
     * День карты активности. count — всё вместе (ответы в работах и задачи курсов), courseCount — из них задачи курсов.
     * level 0–4 — насыщенность клетки; -1 — клетка вне диапазона (не рисуется).
     */
    public record Day(LocalDate date, int count, int courseCount, int level, String title) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long studentId) {
        Instant now = Instant.now();
        List<Assignment> assignments = assignmentService.forStudent(studentId);
        Map<Long, Assignment> byId = assignments.stream().collect(Collectors.toMap(Assignment::getId, Function.identity()));
        List<Attempt> attempts = attemptService.forAssignments(assignments.stream().map(Assignment::getId).toList());
        Set<Long> visibleAttempts = new HashSet<>();
        for (Attempt attempt : attempts) {
            if (ResultVisibility.visibleToStudent(byId.get(attempt.getAssignment().getId()), attempt, now)) {
                visibleAttempts.add(attempt.getId());
            }
        }
        List<AttemptAnswer> answers = attempts.isEmpty() ? List.of()
                : answerRepository.findByAttemptIdInOrderByAttemptIdAscPositionAsc(attempts.stream().map(Attempt::getId).toList())
                .stream().filter(a -> a.getAnswer() != null).toList();
        Map<Long, Task> tasks = answers.isEmpty() ? Map.of()
                : taskService.tasksById(answers.stream().map(AttemptAnswer::getTaskId).distinct().toList());

        int checked = 0;
        int correct = 0;
        Map<Integer, int[]> numbers = new TreeMap<>(); // номер → {решено, проверено, верно}
        Map<LocalDate, Integer> perDay = new HashMap<>();
        for (AttemptAnswer answer : answers) {
            int[] row = numbers.computeIfAbsent(tasks.get(answer.getTaskId()).getEgeNumber(), n -> new int[3]);
            row[0]++;
            if (visibleAttempts.contains(answer.getAttemptId()) && answer.getIsCorrect() != null) {
                row[1]++;
                checked++;
                if (answer.getIsCorrect()) {
                    row[2]++;
                    correct++;
                }
            }
            if (answer.getLastSavedAt() != null) {
                perDay.merge(answer.getLastSavedAt().atZone(dates.zone()).toLocalDate(), 1, Integer::sum);
            }
        }
        List<NumberRow> byNumber = new ArrayList<>();
        numbers.forEach((n, r) -> byNumber.add(new NumberRow(n, r[0], r[1], r[2])));

        LocalDate today = now.atZone(dates.zone()).toLocalDate();
        Map<LocalDate, Integer> coursePerDay = coursePerDay(studentId, today);
        coursePerDay.forEach((day, n) -> perDay.merge(day, n, Integer::sum));
        List<Week> weeks = weeks(perDay, coursePerDay, today);
        LocalDate from = today.minusDays(365);
        int yearTotal = perDay.entrySet().stream().filter(e -> e.getKey().isAfter(from)).mapToInt(Map.Entry::getValue).sum();
        int activeDays = (int) perDay.keySet().stream().filter(d -> d.isAfter(from)).count();
        return new Dashboard(answers.size(), checked, correct, byNumber, weeks, yearTotal, activeDays);
    }

    /** Задачи курсов по дням: сколько разных задач ученик проверял в этот день (повторные проверки не считаются). */
    private Map<LocalDate, Integer> coursePerDay(Long studentId, LocalDate today) {
        Instant from = today.minusWeeks(WEEKS + 1L).atStartOfDay(dates.zone()).toInstant();
        Map<LocalDate, Set<String>> tasksPerDay = new HashMap<>();
        for (CourseProgressRepository.AttemptTime a : courseProgress.attemptTimes(studentId, from)) {
            tasksPerDay.computeIfAbsent(a.at().atZone(dates.zone()).toLocalDate(), d -> new HashSet<>()).add(a.taskId());
        }
        Map<LocalDate, Integer> result = new HashMap<>();
        tasksPerDay.forEach((day, ids) -> result.put(day, ids.size()));
        return result;
    }

    static List<Week> weeks(Map<LocalDate, Integer> perDay, LocalDate today) {
        return weeks(perDay, Map.of(), today);
    }

    /** Карта активности: WEEKS недель, последняя — текущая; дни после сегодняшнего не рисуются. */
    static List<Week> weeks(Map<LocalDate, Integer> perDay, Map<LocalDate, Integer> coursePerDay, LocalDate today) {
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(WEEKS - 1L);
        List<Week> weeks = new ArrayList<>();
        int lastMonth = -1;
        for (int w = 0; w < WEEKS; w++) {
            LocalDate monday = start.plusWeeks(w);
            List<Day> days = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                LocalDate date = monday.plusDays(d);
                if (date.isAfter(today)) {
                    days.add(new Day(date, 0, 0, -1, ""));
                    continue;
                }
                int count = perDay.getOrDefault(date, 0);
                int course = coursePerDay.getOrDefault(date, 0);
                String title = DAY_TITLE.format(date) + ": " + tasksWord(count)
                        + (course > 0 ? " (в курсах: " + course + ")" : "");
                days.add(new Day(date, count, course, level(count), title));
            }
            String label = "";
            if (monday.getMonthValue() != lastMonth) {
                lastMonth = monday.getMonthValue();
                label = w == 0 ? "" : MONTH.format(monday).replace(".", "");
            }
            weeks.add(new Week(label, days));
        }
        return weeks;
    }

    static int level(int count) {
        if (count <= 0) {
            return 0;
        }
        return count <= 2 ? 1 : count <= 5 ? 2 : count <= 9 ? 3 : 4;
    }

    static String tasksWord(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        String word = mod100 >= 11 && mod100 <= 14 ? "заданий"
                : mod10 == 1 ? "задание" : mod10 >= 2 && mod10 <= 4 ? "задания" : "заданий";
        return count == 0 ? "нет решённых заданий" : count + " " + word;
    }

    private static int percent(int part, int whole) {
        return whole == 0 ? 0 : (int) Math.round(100.0 * part / whole);
    }
}
