package com.github.anton.infoege.taskset;

import com.github.anton.infoege.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "task_set_item")
@IdClass(TaskSetItem.Key.class)
public class TaskSetItem {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_set_id")
    private TaskSet taskSet;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false)
    private int position;

    protected TaskSetItem() {
    }

    TaskSetItem(TaskSet taskSet, Task task, int position) {
        this.taskSet = taskSet;
        this.task = task;
        this.position = position;
    }

    public TaskSet getTaskSet() {
        return taskSet;
    }

    public Task getTask() {
        return task;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }

    /** Составной ключ (task_set_id, task_id). */
    public static class Key implements Serializable {

        private Long taskSet;
        private Long task;

        public Key() {
        }

        public Key(Long taskSet, Long task) {
            this.taskSet = taskSet;
            this.task = task;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(taskSet, other.taskSet) && Objects.equals(task, other.task);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskSet, task);
        }
    }
}
