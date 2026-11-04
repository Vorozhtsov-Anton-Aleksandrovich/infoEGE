package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "attempt")
public class Attempt {

    /**
     * Запас на сетевую задержку: сохранение, отправленное в последнюю секунду, ещё принимается.
     * Время окончания для ученика и в отчётах остаётся expiresAt.
     */
    public static final Duration GRACE = Duration.ofSeconds(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant expiresAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    private Integer score;

    private Instant lastHeartbeatAt;

    /** Балл, выставленный преподавателем вручную; заменяет автоматический score. */
    private Integer manualScore;

    private String manualComment;

    private Instant manualUpdatedAt;

    protected Attempt() {
    }

    public Attempt(Assignment assignment, Instant startedAt, Instant expiresAt) {
        this.assignment = assignment;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isOverdue(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt.plus(GRACE));
    }

    /** Закрывает попытку по истечении времени. */
    public void expire() {
        status = AttemptStatus.EXPIRED;
        finishedAt = expiresAt;
    }

    public void finish(Instant now) {
        status = AttemptStatus.FINISHED;
        finishedAt = expiresAt != null && now.isAfter(expiresAt) ? expiresAt : now;
    }

    /** Секунды до конца по часам сервера; null, если лимита нет. */
    public Long remainingSeconds(Instant now) {
        if (expiresAt == null) {
            return null;
        }
        return Math.max(0, Duration.between(now, expiresAt).toSeconds());
    }

    /** Итоговый балл: ручной, если выставлен, иначе автоматический. */
    public Integer getFinalScore() {
        return manualScore != null ? manualScore : score;
    }

    public void setManualScore(Integer manualScore, String comment, Instant now) {
        this.manualScore = manualScore;
        this.manualComment = comment;
        this.manualUpdatedAt = now;
    }

    /** Общее время: от старта до завершения, для незавершённой попытки до текущего момента. */
    public long totalSeconds(Instant now) {
        Instant end = finishedAt != null ? finishedAt : now;
        return Math.max(0, Duration.between(startedAt, end).toSeconds());
    }

    public boolean isInProgress() {
        return status == AttemptStatus.IN_PROGRESS;
    }

    public Long getId() {
        return id;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getManualScore() {
        return manualScore;
    }

    public String getManualComment() {
        return manualComment;
    }

    public Instant getManualUpdatedAt() {
        return manualUpdatedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
