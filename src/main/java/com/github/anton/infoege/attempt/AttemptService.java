package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentRepository;
import com.github.anton.infoege.checking.AnswerChecker;
import com.github.anton.infoege.task.TaskFile;
import com.github.anton.infoege.task.TaskFileRepository;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetItem;
import com.github.anton.infoege.taskset.TaskSetRepository;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AttemptService {

    /** Максимальный прирост времени на задание за один heartbeat (интервал клиента 15 с плюс запас). */
    public static final int MAX_HEARTBEAT_SECONDS = 20;

    /** Ограничение длины одной ячейки ответа. */
    public static final int MAX_CELL_LENGTH = 100;

    private final AttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final AssignmentRepository assignmentRepository;
    private final TaskSetRepository taskSetRepository;
    private final TaskFileRepository taskFileRepository;

    public AttemptService(AttemptRepository attemptRepository, AttemptAnswerRepository answerRepository,
                          AssignmentRepository assignmentRepository, TaskSetRepository taskSetRepository,
                          TaskFileRepository taskFileRepository) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.assignmentRepository = assignmentRepository;
        this.taskSetRepository = taskSetRepository;
        this.taskFileRepository = taskFileRepository;
    }

    /** Попытка закрыта (завершена или время вышло): изменения больше не принимаются. */
    public static class AttemptClosedException extends RuntimeException {
        private final AttemptStatus status;

        public AttemptClosedException(AttemptStatus status) {
            super("Attempt is " + status);
            this.status = status;
        }

        public AttemptStatus getStatus() {
            return status;
        }
    }

    /** Работу нельзя начать: срок назначения прошёл. */
    public static class DeadlinePassedException extends RuntimeException {
        public DeadlinePassedException() {
            super("Deadline passed");
        }
    }

    /**
     * Начинает попытку или возвращает уже начатую: одна попытка на назначение.
     * Задания, правильные ответы и баллы копируются из подборки в момент старта.
     */
    @Transactional
    public Attempt start(Long assignmentId, Long studentId) {
        Assignment assignment = ownAssignment(assignmentId, studentId);
        var existing = attemptRepository.findByAssignmentId(assignmentId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = Instant.now();
        if (assignment.getDeadlineAt() != null && !now.isBefore(assignment.getDeadlineAt())) {
            throw new DeadlinePassedException();
        }
        TaskSet set = taskSetRepository.findWithItemsById(assignment.getTaskSet().getId()).orElseThrow();
        Attempt attempt = attemptRepository.saveAndFlush(
                new Attempt(assignment, now, expiresAt(now, set.getTimeLimitMinutes(), assignment.getDeadlineAt())));
        List<AttemptAnswer> answers = new ArrayList<>();
        for (TaskSetItem item : set.getItems()) {
            answers.add(new AttemptAnswer(attempt.getId(), item.getTask(), item.getPosition()));
        }
        answerRepository.saveAll(answers);
        return attempt;
    }

    /**
     * Конец попытки: старт плюс лимит времени, но не позже срока назначения.
     * Без лимита и без срока попытка не истекает.
     */
    static Instant expiresAt(Instant start, Integer timeLimitMinutes, Instant deadline) {
        Instant byLimit = timeLimitMinutes == null ? null : start.plus(Duration.ofMinutes(timeLimitMinutes));
        if (byLimit == null) {
            return deadline;
        }
        return deadline == null || byLimit.isBefore(deadline) ? byLimit : deadline;
    }

    /** Попытка ученика с проверкой истечения времени. Используется при открытии тренажёра. */
    @Transactional
    public Attempt open(Long attemptId, Long studentId) {
        return lockOwn(attemptId, studentId);
    }

    @Transactional(readOnly = true)
    public List<AttemptAnswer> answers(Long attemptId) {
        return answerRepository.findByAttemptIdOrderByPosition(attemptId);
    }

    /** Результат сохранения или heartbeat для клиента: только статус и время, без правильных ответов. */
    public record AttemptState(AttemptStatus status, Long remainingSeconds) {
    }

    /**
     * Сохраняет ответ. Пустые ячейки допустимы; если пусты все, ответ считается стёртым.
     * Форма ответа должна совпадать со снимком задания.
     */
    @Transactional(noRollbackFor = AttemptClosedException.class)
    public AttemptState saveAnswer(Long attemptId, Long studentId, Long taskId, List<List<String>> cells) {
        Attempt attempt = lockOwnInProgress(attemptId, studentId);
        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attemptId, taskId)
                .orElseThrow(NotFoundException::new);
        answer.saveAnswer(normalize(cells, answer.rows(), answer.cols()), Instant.now());
        return state(attempt);
    }

    /**
     * Учёт времени на задание. Клиент присылает, сколько секунд вкладка была видимой с прошлого heartbeat;
     * сервер не засчитывает больше, чем прошло с прошлого heartbeat этой попытки, и больше лимита на запрос.
     */
    @Transactional(noRollbackFor = AttemptClosedException.class)
    public AttemptState heartbeat(Long attemptId, Long studentId, Long taskId, int clientSeconds) {
        Attempt attempt = lockOwnInProgress(attemptId, studentId);
        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attemptId, taskId)
                .orElseThrow(NotFoundException::new);
        Instant now = Instant.now();
        Instant since = attempt.getLastHeartbeatAt() != null ? attempt.getLastHeartbeatAt() : attempt.getStartedAt();
        long elapsed = Math.max(0, Duration.between(since, now).toSeconds());
        int credit = (int) Math.min(Math.min(Math.max(clientSeconds, 0), elapsed), MAX_HEARTBEAT_SECONDS);
        answer.addTimeSpent(credit);
        answer.markOpened(now);
        attempt.setLastHeartbeatAt(now);
        return state(attempt);
    }

    @Transactional
    public Attempt finish(Long attemptId, Long studentId) {
        Attempt attempt = lockOwn(attemptId, studentId);
        if (attempt.isInProgress()) {
            attempt.finish(Instant.now());
            grade(attempt);
        }
        return attempt;
    }

    /** Файл задания для ученика: задание должно входить в его попытку. */
    @Transactional(readOnly = true)
    public TaskFile file(Long attemptId, Long studentId, Long fileId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getAssignment().getStudent().getId().equals(studentId))
                .orElseThrow(NotFoundException::new);
        TaskFile file = taskFileRepository.findById(fileId).orElseThrow(NotFoundException::new);
        if (!answerRepository.existsByAttemptIdAndTaskId(attempt.getId(), file.getTaskId())) {
            throw new NotFoundException();
        }
        return file;
    }

    /** Закрывает и проверяет все просроченные попытки. Вызывается планировщиком и перед построением отчётов. */
    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now();
        int expired = 0;
        for (Long id : attemptRepository.findOverdueIds(now.minus(Attempt.GRACE))) {
            Attempt attempt = attemptRepository.findForUpdate(id).orElseThrow();
            if (attempt.isInProgress() && attempt.isOverdue(now)) {
                attempt.expire();
                grade(attempt);
                expired++;
            }
        }
        return expired;
    }

    /**
     * Автопроверка: баллы по снимку правильного ответа и стратегии, итог в attempt.score.
     * Ручной балл преподавателя не трогается.
     */
    private void grade(Attempt attempt) {
        int total = 0;
        for (AttemptAnswer answer : answerRepository.findByAttemptIdOrderByPosition(attempt.getId())) {
            AnswerChecker.Result result = AnswerChecker.check(answer.getAnswer(), answer.getAnswerSnapshot(),
                    answer.getScoringSnapshot(), answer.getPointsSnapshot());
            answer.setIsCorrect(result.correct());
            answer.setPoints(result.points());
            total += result.points();
        }
        attempt.setScore(total);
    }

    @Transactional(readOnly = true)
    public List<Attempt> forAssignments(List<Long> assignmentIds) {
        return assignmentIds.isEmpty() ? List.of() : attemptRepository.findByAssignmentIdIn(assignmentIds);
    }

    /** С блокировкой строки назначения: два одновременных старта не создадут две попытки. */
    private Assignment ownAssignment(Long assignmentId, Long studentId) {
        return assignmentRepository.findForUpdate(assignmentId)
                .filter(a -> a.getStudent().getId().equals(studentId))
                .orElseThrow(NotFoundException::new);
    }

    private Attempt lockOwn(Long attemptId, Long studentId) {
        Attempt attempt = attemptRepository.findOwnForUpdate(attemptId, studentId).orElseThrow(NotFoundException::new);
        if (attempt.isInProgress() && attempt.isOverdue(Instant.now())) {
            attempt.expire();
            grade(attempt);
        }
        return attempt;
    }

    private Attempt lockOwnInProgress(Long attemptId, Long studentId) {
        Attempt attempt = lockOwn(attemptId, studentId);
        if (!attempt.isInProgress()) {
            throw new AttemptClosedException(attempt.getStatus());
        }
        return attempt;
    }

    private static AttemptState state(Attempt attempt) {
        return new AttemptState(attempt.getStatus(), attempt.remainingSeconds(Instant.now()));
    }

    private static List<List<String>> normalize(List<List<String>> cells, int rows, int cols) {
        if (cells == null || cells.size() != rows || cells.stream().anyMatch(r -> r == null || r.size() != cols)) {
            throw new IllegalArgumentException("Answer shape must be " + rows + "x" + cols);
        }
        List<List<String>> result = new ArrayList<>();
        boolean empty = true;
        for (List<String> row : cells) {
            List<String> cleanRow = new ArrayList<>();
            for (String cell : row) {
                String value = cell == null ? "" : cell.trim();
                if (value.length() > MAX_CELL_LENGTH) {
                    throw new IllegalArgumentException("Cell is too long");
                }
                empty &= value.isEmpty();
                cleanRow.add(value);
            }
            result.add(cleanRow);
        }
        return empty ? null : result;
    }
}
