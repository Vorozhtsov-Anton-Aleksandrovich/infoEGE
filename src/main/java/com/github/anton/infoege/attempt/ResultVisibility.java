package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;

import java.time.Instant;

/**
 * Когда ученику можно показать результаты, правильные ответы и разборы (настройка showResultsMode).
 */
public final class ResultVisibility {

    private ResultVisibility() {
    }

    public static boolean visibleToStudent(Assignment assignment, Attempt attempt, Instant now) {
        if (attempt == null || attempt.isInProgress()) {
            return false;
        }
        return switch (assignment.getShowResultsMode()) {
            case AFTER_FINISH, INSTANT -> true;
            case AFTER_DEADLINE -> assignment.getDeadlineAt() != null && !now.isBefore(assignment.getDeadlineAt());
            case TEACHER_ONLY -> false;
        };
    }
}
