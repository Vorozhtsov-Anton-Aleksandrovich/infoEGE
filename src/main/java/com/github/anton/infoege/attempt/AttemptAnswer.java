package com.github.anton.infoege.attempt;

import com.github.anton.infoege.task.Scoring;
import com.github.anton.infoege.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ответ ученика на одно задание попытки.
 * Правильный ответ, баллы и стратегия копируются при старте, чтобы правка задания не меняла начатую попытку.
 * timeSpentSeconds, answerChanges и firstOpenedAt нужны также для будущей автооценки сложности.
 */
@Entity
@Table(name = "attempt_answer")
@IdClass(AttemptAnswer.Key.class)
public class AttemptAnswer {

    @Id
    private Long attemptId;

    @Id
    private Long taskId;

    @Column(nullable = false)
    private int position;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<List<String>> answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<List<String>> answerSnapshot;

    @Column(nullable = false)
    private int pointsSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scoring scoringSnapshot;

    private Boolean isCorrect;

    private Integer points;

    @Column(nullable = false)
    private int timeSpentSeconds;

    @Column(nullable = false)
    private int answerChanges;

    /** Число проверок в режиме «Проверить ответ». */
    @Column(nullable = false)
    private int checkCount;

    private Instant firstOpenedAt;

    private Instant lastSavedAt;

    protected AttemptAnswer() {
    }

    public AttemptAnswer(Long attemptId, Task task, int position) {
        this.attemptId = attemptId;
        this.taskId = task.getId();
        this.position = position;
        this.answerSnapshot = task.getCorrectAnswer();
        this.pointsSnapshot = task.getMaxPoints();
        this.scoringSnapshot = task.getScoring();
    }

    /** Число строк и столбцов ответа берётся из снимка, а не из текущего задания. */
    public int rows() {
        return answerSnapshot.size();
    }

    public int cols() {
        return answerSnapshot.get(0).size();
    }

    public void markOpened(Instant now) {
        if (firstOpenedAt == null) {
            firstOpenedAt = now;
        }
    }

    /** Сохраняет ответ; null означает «ответ стёрт». Смена ответа считается только при реальном изменении. */
    public void saveAnswer(List<List<String>> newAnswer, Instant now) {
        if (!Objects.equals(answer, newAnswer)) {
            answerChanges++;
        }
        answer = newAnswer;
        lastSavedAt = now;
        markOpened(now);
    }

    /**
     * Проверка в режиме «Проверить ответ»: сохраняет ответ и считает попытку.
     * Повторная проверка того же ответа и проверка пустого ответа попыткой не считаются.
     */
    public void checkAnswer(List<List<String>> newAnswer, Instant now) {
        if (newAnswer != null && (checkCount == 0 || !Objects.equals(answer, newAnswer))) {
            checkCount++;
        }
        saveAnswer(newAnswer, now);
    }

    public int getCheckCount() {
        return checkCount;
    }

    public void addTimeSpent(int seconds) {
        timeSpentSeconds += seconds;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public int getPosition() {
        return position;
    }

    public List<List<String>> getAnswer() {
        return answer;
    }

    public List<List<String>> getAnswerSnapshot() {
        return answerSnapshot;
    }

    public int getPointsSnapshot() {
        return pointsSnapshot;
    }

    public Scoring getScoringSnapshot() {
        return scoringSnapshot;
    }

    public Boolean getIsCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect) {
        this.isCorrect = isCorrect;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public int getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public int getAnswerChanges() {
        return answerChanges;
    }

    public Instant getFirstOpenedAt() {
        return firstOpenedAt;
    }

    public Instant getLastSavedAt() {
        return lastSavedAt;
    }

    public static class Key implements Serializable {

        private Long attemptId;
        private Long taskId;

        public Key() {
        }

        public Key(Long attemptId, Long taskId) {
            this.attemptId = attemptId;
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(attemptId, other.attemptId) && Objects.equals(taskId, other.taskId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attemptId, taskId);
        }
    }
}
