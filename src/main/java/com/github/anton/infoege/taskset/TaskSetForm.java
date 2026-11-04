package com.github.anton.infoege.taskset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TaskSetForm {

    @NotNull
    private TaskSetType type = TaskSetType.COLLECTION;

    @NotBlank(message = "Укажите название")
    @Size(max = 300, message = "Не длиннее 300 символов")
    private String title;

    private String description;

    @Min(value = 1, message = "От 1 до 600 минут")
    @Max(value = 600, message = "От 1 до 600 минут")
    private Integer timeLimitMinutes;

    public static TaskSetForm from(TaskSet set) {
        TaskSetForm form = new TaskSetForm();
        form.type = set.getType();
        form.title = set.getTitle();
        form.description = set.getDescription();
        form.timeLimitMinutes = set.getTimeLimitMinutes();
        return form;
    }

    public void applyTo(TaskSet set) {
        set.setType(type);
        set.setTitle(title.trim());
        set.setDescription(description == null || description.isBlank() ? null : description.trim());
        set.setTimeLimitMinutes(timeLimitMinutes);
        set.normalizeOrder();
    }

    public TaskSetType getType() {
        return type;
    }

    public void setType(TaskSetType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public void setTimeLimitMinutes(Integer timeLimitMinutes) {
        this.timeLimitMinutes = timeLimitMinutes;
    }
}
