package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;

import java.time.Instant;

/**
 * Статус назначения для показа: объединяет попытку и срок назначения.
 */
public enum WorkStatus {
    NOT_STARTED("не начато"),
    IN_PROGRESS("в процессе"),
    PAUSED("на паузе"),
    FINISHED("завершено"),
    EXPIRED("время вышло"),
    OVERDUE("просрочено");

    private final String label;

    WorkStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * @param attempt попытка назначения или null, если ученик не начинал
     */
    public static WorkStatus of(Assignment assignment, Attempt attempt, Instant now) {
        if (attempt == null) {
            boolean deadlinePassed = assignment.getDeadlineAt() != null && !now.isBefore(assignment.getDeadlineAt());
            return deadlinePassed ? OVERDUE : NOT_STARTED;
        }
        return switch (attempt.getStatus()) {
            case IN_PROGRESS -> attempt.isPaused()
                    ? (assignment.getDeadlineAt() != null && !now.isBefore(assignment.getDeadlineAt()) ? EXPIRED : PAUSED)
                    : attempt.isOverdue(now) ? EXPIRED : IN_PROGRESS;
            case FINISHED -> FINISHED;
            case EXPIRED -> EXPIRED;
        };
    }
}
