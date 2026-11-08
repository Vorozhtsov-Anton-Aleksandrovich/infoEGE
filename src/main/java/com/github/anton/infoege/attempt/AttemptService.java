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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    /** Статус для клиента, когда попытка на паузе (изменения не принимаются, пока ученик не продолжит). */
    public static final String PAUSED = "PAUSED";

    /** Попытка закрыта (завершена, время вышло) или на паузе: изменения не принимаются. */
    public static class AttemptClosedException extends RuntimeException {
        private final String status;

        public AttemptClosedException(String status) {
            super("Attempt is " + status);
            this.status = status;
        }

        public String getStatus() {
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

    /**
     * Попытка ученика с проверкой истечения времени. Используется при открытии тренажёра:
     * попытка на паузе продолжается, время снова идёт.
     */
    @Transactional
    public Attempt open(Long attemptId, Long studentId) {
        Attempt attempt = lockOwn(attemptId, studentId);
        if (attempt.isInProgress() && attempt.isPaused()) {
            attempt.resume(Instant.now(), attempt.getAssignment().getDeadlineAt());
            attempt.setLastHeartbeatAt(Instant.now()); // время паузы не засчитывается в время на задание
        }
        return attempt;
    }

    /**
     * «Закончить потом»: ставит попытку на паузу, время останавливается до следующего открытия тренажёра.
     * Только если преподаватель разрешил паузу в назначении.
     */
    @Transactional(noRollbackFor = AttemptClosedException.class)
    public Attempt pause(Long attemptId, Long studentId) {
        Attempt attempt = lockOwn(attemptId, studentId);
        if (!attempt.isInProgress()) {
            throw new AttemptClosedException(attempt.getStatus().name());
        }
        if (!attempt.getAssignment().isAllowPause()) {
            throw new IllegalArgumentException("Pause is not allowed for this assignment");
        }
        attempt.pause(Instant.now());
        return attempt;
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
     * Вердикт проверки для ученика. Правильный ответ не раскрывается, только верно/неверно и баллы.
     *
     * @param verdict correct, partial, wrong или null, если ответ пуст
     */
    public record CheckState(AttemptStatus status, Long remainingSeconds, String verdict, Integer points,
                             int maxPoints, int checks) {
    }

    /** Вердикт по ответу: correct, partial (часть баллов при построчной проверке), wrong; null — ответа нет. */
    public static String verdict(AttemptAnswer answer) {
        if (answer.getAnswer() == null) {
            return null;
        }
        AnswerChecker.Result result = check(answer);
        return result.correct() ? "correct" : result.points() > 0 ? "partial" : "wrong";
    }

    /**
     * «Проверить ответ»: сохраняет ответ, считает попытку и сразу возвращает вердикт.
     * Доступно только в назначениях с режимом INSTANT; в зачёт при завершении идёт последний ответ.
     */
    @Transactional(noRollbackFor = AttemptClosedException.class)
    public CheckState checkAnswer(Long attemptId, Long studentId, Long taskId, List<List<String>> cells) {
        Attempt attempt = lockOwnInProgress(attemptId, studentId);
        if (!attempt.getAssignment().getShowResultsMode().isInstant()) {
            throw new IllegalArgumentException("Instant check is not enabled for this assignment");
        }
        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attemptId, taskId)
                .orElseThrow(NotFoundException::new);
        answer.checkAnswer(normalize(cells, answer.rows(), answer.cols()), Instant.now());
        Integer points = answer.getAnswer() == null ? null : check(answer).points();
        return new CheckState(attempt.getStatus(), attempt.remainingSeconds(Instant.now()), verdict(answer), points,
                answer.getPointsSnapshot(), answer.getCheckCount());
    }

    private static AnswerChecker.Result check(AttemptAnswer answer) {
        return AnswerChecker.check(answer.getAnswer(), answer.getAnswerSnapshot(),
                answer.getScoringSnapshot(), answer.getPointsSnapshot());
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
            attempt.resume(Instant.now(), attempt.getAssignment().getDeadlineAt());
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
        Set<Long> ids = new LinkedHashSet<>(attemptRepository.findOverdueIds(now.minus(Attempt.GRACE)));
        ids.addAll(attemptRepository.findPausedPastDeadlineIds(now.minus(Attempt.GRACE)));
        for (Long id : ids) {
            Attempt attempt = attemptRepository.findForUpdate(id).orElseThrow();
            if (closeIfOverdue(attempt, now)) {
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
            AnswerChecker.Result result = check(answer);
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
        closeIfOverdue(attempt, Instant.now());
        return attempt;
    }

    /**
     * Закрывает и проверяет попытку, если время вышло: по лимиту (не на паузе) или по сроку назначения
     * (в том числе на паузе — срок пауза не продлевает).
     */
    private boolean closeIfOverdue(Attempt attempt, Instant now) {
        if (!attempt.isInProgress()) {
            return false;
        }
        Instant deadline = attempt.getAssignment().getDeadlineAt();
        boolean pastDeadline = attempt.isPaused() && deadline != null && !now.isBefore(deadline.plus(Attempt.GRACE));
        if (!attempt.isOverdue(now) && !pastDeadline) {
            return false;
        }
        attempt.expire();
        grade(attempt);
        return true;
    }

    private Attempt lockOwnInProgress(Long attemptId, Long studentId) {
        Attempt attempt = lockOwn(attemptId, studentId);
        if (!attempt.isInProgress()) {
            throw new AttemptClosedException(attempt.getStatus().name());
        }
        if (attempt.isPaused()) {
            throw new AttemptClosedException(PAUSED);
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
