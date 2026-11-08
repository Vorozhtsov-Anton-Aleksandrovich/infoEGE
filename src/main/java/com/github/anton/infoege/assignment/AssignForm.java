package com.github.anton.infoege.assignment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AssignForm {

    @NotEmpty(message = "Выберите хотя бы одного ученика")
    private List<Long> studentIds = new ArrayList<>();

    /** Местное время из поля datetime-local, необязательно. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime deadline;

    @NotNull
    private ShowResultsMode showResultsMode = ShowResultsMode.AFTER_FINISH;

    /** Разрешить паузу («Закончить потом»). */
    private boolean allowPause;

    public boolean isAllowPause() {
        return allowPause;
    }

    public void setAllowPause(boolean allowPause) {
        this.allowPause = allowPause;
    }

    public List<Long> getStudentIds() {
        return studentIds;
    }

    public void setStudentIds(List<Long> studentIds) {
        this.studentIds = studentIds;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public ShowResultsMode getShowResultsMode() {
        return showResultsMode;
    }

    public void setShowResultsMode(ShowResultsMode showResultsMode) {
        this.showResultsMode = showResultsMode;
    }
}
