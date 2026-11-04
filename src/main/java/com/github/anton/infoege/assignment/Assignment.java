package com.github.anton.infoege.assignment;

import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "assignment")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_set_id")
    private TaskSet taskSet;

    @Column(nullable = false)
    private Instant assignedAt = Instant.now();

    private Instant deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShowResultsMode showResultsMode;

    protected Assignment() {
    }

    public Assignment(User student, TaskSet taskSet, Instant deadlineAt, ShowResultsMode showResultsMode) {
        this.student = student;
        this.taskSet = taskSet;
        this.deadlineAt = deadlineAt;
        this.showResultsMode = showResultsMode;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public TaskSet getTaskSet() {
        return taskSet;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public ShowResultsMode getShowResultsMode() {
        return showResultsMode;
    }
}
