package com.github.anton.infoege.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int egeNumber;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String statementMd;

    @Column(nullable = false)
    private int difficulty;

    @Column(nullable = false)
    private int maxPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Scoring scoring;

    @Column(nullable = false)
    private int answerRows;

    @Column(nullable = false)
    private int answerCols;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<List<String>> correctAnswer;

    private String solutionMd;

    private String topic;

    @Column(nullable = false)
    private boolean archived;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public int getEgeNumber() {
        return egeNumber;
    }

    public void setEgeNumber(int egeNumber) {
        this.egeNumber = egeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatementMd() {
        return statementMd;
    }

    public void setStatementMd(String statementMd) {
        this.statementMd = statementMd;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public int getAnswerRows() {
        return answerRows;
    }

    public void setAnswerRows(int answerRows) {
        this.answerRows = answerRows;
    }

    public int getAnswerCols() {
        return answerCols;
    }

    public void setAnswerCols(int answerCols) {
        this.answerCols = answerCols;
    }

    public List<List<String>> getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(List<List<String>> correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getSolutionMd() {
        return solutionMd;
    }

    public void setSolutionMd(String solutionMd) {
        this.solutionMd = solutionMd;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
