package com.github.anton.infoege.result;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentRepository;
import com.github.anton.infoege.attempt.Attempt;
import com.github.anton.infoege.attempt.AttemptAnswer;
import com.github.anton.infoege.attempt.AttemptAnswerRepository;
import com.github.anton.infoege.attempt.AttemptRepository;
import com.github.anton.infoege.attempt.AttemptService;
import com.github.anton.infoege.attempt.ResultVisibility;
import com.github.anton.infoege.attempt.WorkStatus;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Результаты для преподавателя и ученика. Все отчёты строятся из попыток и снимков в attempt_answer.
 */
@Service
public class ResultService {

    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final AttemptService attemptService;
    private final TaskService taskService;

    public ResultService(AssignmentRepository assignmentRepository, AttemptRepository attemptRepository,
                         AttemptAnswerRepository answerRepository, AttemptService attemptService,
                         TaskService taskService) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.attemptService = attemptService;
        this.taskService = taskService;
    }

    /**
     * Итог по назначению. attempt, maxScore и totalSeconds пусты, если ученик не начинал.
     */
    public record AssignmentResult(Assignment assignment, Attempt attempt, WorkStatus status,
                                   Integer maxScore, Long totalSeconds, List<AttemptAnswer> answers) {

        public Integer finalScore() {
            return attempt == null ? null : attempt.getFinalScore();
        }

        public boolean isClosed() {
            return attempt != null && !attempt.isInProgress();
        }

        /** Выполнено (дан ответ) и верно по заданиям этой работы. «Верно» известно только после завершения. */
        public TaskStats stats() {
            return TaskStats.of(answers);
        }
    }

    /**
     * Задания: всего, выполнено (есть ответ) и решено верно.
     * Процент правильности считается от всех заданий, процент выполнения — тоже.
     */
    public record TaskStats(int tasks, int answered, int correct) {

        public static final TaskStats EMPTY = new TaskStats(0, 0, 0);

        public static TaskStats of(List<AttemptAnswer> answers) {
            return new TaskStats(answers.size(),
                    (int) answers.stream().filter(a -> a.getAnswer() != null).count(),
                    (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count());
        }

        public TaskStats plus(TaskStats other) {
            return new TaskStats(tasks + other.tasks, answered + other.answered, correct + other.correct);
        }

        public int correctPercent() {
            return tasks == 0 ? 0 : (int) Math.round(100.0 * correct / tasks);
        }

        public int answeredPercent() {
            return tasks == 0 ? 0 : (int) Math.round(100.0 * answered / tasks);
        }
    }

    /** Ответ на одно задание вместе с заданием из банка (название, номер, разбор). */
    public record TaskResult(int number, Task task, AttemptAnswer answer) {
    }

    /** Сводка по номеру ЕГЭ: сколько заданий решено и сколько времени ушло. */
    public record NumberStat(int egeNumber, int tasks, int correct, int points, int maxPoints, long seconds) {

        public long averageSeconds() {
            return tasks == 0 ? 0 : seconds / tasks;
        }
    }

    /** stats — по заданиям всех сданных работ. */
    public record StudentSummary(List<AssignmentResult> results, int closed, int points, int maxPoints,
                                 long totalSeconds, List<NumberStat> byNumber, TaskStats stats) {
    }

    /** Выполнено и верно по сданным работам каждого ученика: id ученика → статистика. */
    @Transactional
    public Map<Long, TaskStats> statsByStudent() {
        Map<Long, TaskStats> stats = new java.util.HashMap<>();
        for (AssignmentResult r : results(null, null)) {
            if (r.isClosed()) {
                stats.merge(r.assignment().getStudent().getId(), r.stats(), TaskStats::plus);
            }
        }
        return stats;
    }

    /** Все назначения с результатами; фильтры необязательны. */
    @Transactional
    public List<AssignmentResult> results(Long taskSetId, Long studentId) {
        attemptService.expireOverdue();
        List<Assignment> assignments = assignmentRepository.findAllWithStudentAndSet().stream()
                .filter(a -> taskSetId == null || a.getTaskSet().getId().equals(taskSetId))
                .filter(a -> studentId == null || a.getStudent().getId().equals(studentId))
                .toList();
        return build(assignments);
    }

    @Transactional
    public AssignmentResult result(Long assignmentId) {
        attemptService.expireOverdue();
        Assignment assignment = assignmentRepository.findWithStudentAndSetById(assignmentId).orElseThrow(NotFoundException::new);
        return build(List.of(assignment)).get(0);
    }

    @Transactional(readOnly = true)
    public List<TaskResult> taskResults(AssignmentResult result) {
        if (result.answers().isEmpty()) {
            return List.of();
        }
        Map<Long, Task> tasks = taskService.tasksById(result.answers().stream().map(AttemptAnswer::getTaskId).toList());
        List<TaskResult> rows = new ArrayList<>();
        for (AttemptAnswer answer : result.answers()) {
            rows.add(new TaskResult(rows.size() + 1, tasks.get(answer.getTaskId()), answer));
        }
        return rows;
    }

    /**
     * Ручная корректировка итогового балла. null снимает корректировку и возвращает автоматический балл.
     */
    @Transactional
    public void adjustScore(Long assignmentId, Integer manualScore, String comment) {
        Attempt attempt = attemptRepository.findByAssignmentId(assignmentId).orElseThrow(NotFoundException::new);
        attempt = attemptRepository.findForUpdate(attempt.getId()).orElseThrow();
        if (attempt.isInProgress()) {
            throw new IllegalStateException("Работа ещё не завершена");
        }
        int max = answerRepository.findByAttemptIdOrderByPosition(attempt.getId()).stream()
                .mapToInt(AttemptAnswer::getPointsSnapshot).sum();
        if (manualScore != null && (manualScore < 0 || manualScore > max)) {
            throw new IllegalArgumentException("Балл должен быть от 0 до " + max);
        }
        String cleanComment = comment == null || comment.isBlank() ? null : comment.trim();
        attempt.setManualScore(manualScore, cleanComment, Instant.now());
    }

    @Transactional
    public StudentSummary studentSummary(Long studentId) {
        List<AssignmentResult> results = results(null, studentId);
        int closed = 0;
        int points = 0;
        int maxPoints = 0;
        long seconds = 0;
        List<AttemptAnswer> answers = new ArrayList<>();
        for (AssignmentResult r : results) {
            if (r.isClosed()) {
                closed++;
                points += Objects.requireNonNullElse(r.finalScore(), 0);
                maxPoints += r.maxScore();
                seconds += r.totalSeconds();
                answers.addAll(r.answers());
            }
        }
        return new StudentSummary(results, closed, points, maxPoints, seconds, byNumber(answers), TaskStats.of(answers));
    }

    /**
     * Результат для ученика: только своё назначение и только когда это разрешено showResultsMode.
     */
    @Transactional
    public AssignmentResult studentResult(Long assignmentId, Long studentId) {
        AssignmentResult result = result(assignmentId);
        if (!result.assignment().getStudent().getId().equals(studentId)
                || !ResultVisibility.visibleToStudent(result.assignment(), result.attempt(), Instant.now())) {
            throw new NotFoundException();
        }
        return result;
    }

    private List<AssignmentResult> build(List<Assignment> assignments) {
        Map<Long, Attempt> attempts = attemptRepository.findByAssignmentIdIn(assignments.stream().map(Assignment::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getAssignment().getId(), Function.identity()));
        List<Long> attemptIds = attempts.values().stream().map(Attempt::getId).toList();
        Map<Long, List<AttemptAnswer>> answers = attemptIds.isEmpty() ? Map.of()
                : answerRepository.findByAttemptIdInOrderByAttemptIdAscPositionAsc(attemptIds).stream()
                .collect(Collectors.groupingBy(AttemptAnswer::getAttemptId));
        Instant now = Instant.now();
        List<AssignmentResult> results = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Attempt attempt = attempts.get(assignment.getId());
            List<AttemptAnswer> attemptAnswers = attempt == null ? List.of() : answers.getOrDefault(attempt.getId(), List.of());
            Integer max = attempt == null ? null : attemptAnswers.stream().mapToInt(AttemptAnswer::getPointsSnapshot).sum();
            Long total = attempt == null ? null : attempt.totalSeconds(now);
            results.add(new AssignmentResult(assignment, attempt, WorkStatus.of(assignment, attempt, now), max, total, attemptAnswers));
        }
        return results;
    }

    private List<NumberStat> byNumber(List<AttemptAnswer> answers) {
        if (answers.isEmpty()) {
            return List.of();
        }
        Map<Long, Task> tasks = taskService.tasksById(answers.stream().map(AttemptAnswer::getTaskId).distinct().toList());
        Map<Integer, List<AttemptAnswer>> grouped = answers.stream()
                .collect(Collectors.groupingBy(a -> tasks.get(a.getTaskId()).getEgeNumber(), TreeMap::new, Collectors.toList()));
        List<NumberStat> stats = new ArrayList<>();
        grouped.forEach((number, list) -> stats.add(new NumberStat(number, list.size(),
                (int) list.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count(),
                list.stream().mapToInt(a -> Objects.requireNonNullElse(a.getPoints(), 0)).sum(),
                list.stream().mapToInt(AttemptAnswer::getPointsSnapshot).sum(),
                list.stream().mapToLong(AttemptAnswer::getTimeSpentSeconds).sum())));
        return stats;
    }
}
