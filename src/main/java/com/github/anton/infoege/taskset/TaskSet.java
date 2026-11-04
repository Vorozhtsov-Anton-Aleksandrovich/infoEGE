package com.github.anton.infoege.taskset;

import com.github.anton.infoege.task.Task;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "task_set")
public class TaskSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskSetType type;

    @Column(nullable = false)
    private String title;

    private String description;

    private Integer timeLimitMinutes;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "taskSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<TaskSetItem> items = new ArrayList<>();

    public boolean contains(Long taskId) {
        return items.stream().anyMatch(item -> item.getTask().getId().equals(taskId));
    }

    /** Добавляет задание в конец. Для варианта порядок затем пересчитывается по номерам. */
    public void addTask(Task task) {
        if (contains(task.getId())) {
            return;
        }
        items.add(new TaskSetItem(this, task, items.size()));
        normalizeOrder();
    }

    public void removeTask(Long taskId) {
        items.removeIf(item -> item.getTask().getId().equals(taskId));
        normalizeOrder();
    }

    /** Сдвигает задание подборки на одну позицию: delta = -1 вверх, +1 вниз. */
    public void move(Long taskId, int delta) {
        if (type == TaskSetType.VARIANT) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getTask().getId().equals(taskId)) {
                int j = i + delta;
                if (j >= 0 && j < items.size()) {
                    items.add(j, items.remove(i));
                }
                break;
            }
        }
        renumber();
    }

    /** Вариант всегда упорядочен по номеру ЕГЭ (при равных номерах сохраняется порядок добавления). */
    public void normalizeOrder() {
        if (type == TaskSetType.VARIANT) {
            items.sort(Comparator.comparingInt((TaskSetItem item) -> item.getTask().getEgeNumber())
                    .thenComparingInt(TaskSetItem::getPosition));
        }
        renumber();
    }

    private void renumber() {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i);
        }
    }

    public Long getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TaskSetItem> getItems() {
        return items;
    }
}
