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

    /** Начало текущей паузы («Закончить потом»); null — попытка не на паузе. */
    private Instant pausedAt;

    /** Сколько секунд попытка провела на паузе (завершённые паузы). */
    @Column(nullable = false)
    private long pausedSeconds;

    protected Attempt() {
    }

    public Attempt(Assignment assignment, Instant startedAt, Instant expiresAt) {
        this.assignment = assignment;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    /** Время вышло. На паузе время не идёт, поэтому попытка на паузе по лимиту не истекает (только по сроку). */
    public boolean isOverdue(Instant now) {
        return pausedAt == null && expiresAt != null && !now.isBefore(expiresAt.plus(GRACE));
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    /** Ставит на паузу: оставшееся время замораживается. */
    public void pause(Instant now) {
        if (pausedAt == null) {
            pausedAt = now;
        }
    }

    /**
     * Снимает с паузы: конец попытки сдвигается на длительность паузы, но не позже срока назначения.
     */
    public void resume(Instant now, Instant deadline) {
        if (pausedAt == null) {
            return;
        }
        long paused = Math.max(0, Duration.between(pausedAt, now).toSeconds());
        pausedSeconds += paused;
        if (expiresAt != null) {
            Instant shifted = expiresAt.plusSeconds(paused);
            expiresAt = deadline != null && shifted.isAfter(deadline) ? deadline : shifted;
        }
        pausedAt = null;
    }

    /** Закрывает попытку по истечении времени. Если она была на паузе (прошёл срок), конец — начало паузы. */
    public void expire() {
        status = AttemptStatus.EXPIRED;
        if (pausedAt != null) {
            finishedAt = expiresAt != null && expiresAt.isBefore(pausedAt) ? expiresAt : pausedAt;
            pausedAt = null;
        } else {
            finishedAt = expiresAt;
        }
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
        Instant at = pausedAt != null ? pausedAt : now;
        return Math.max(0, Duration.between(at, expiresAt).toSeconds());
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

    /** Общее время без пауз: от старта до завершения, для незавершённой попытки до текущего момента или паузы. */
    public long totalSeconds(Instant now) {
        Instant end = finishedAt != null ? finishedAt : pausedAt != null ? pausedAt : now;
        return Math.max(0, Duration.between(startedAt, end).toSeconds() - pausedSeconds);
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public long getPausedSeconds() {
        return pausedSeconds;
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
