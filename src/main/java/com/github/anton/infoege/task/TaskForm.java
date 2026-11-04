package com.github.anton.infoege.task;

import com.github.anton.infoege.checking.AnswerNormalizer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Данные формы задания. Правильный ответ вводится текстом, см. {@link AnswerNormalizer}.
 */
public class TaskForm {

    @NotNull(message = "Укажите номер задания")
    @Min(value = 1, message = "Номер не меньше 1")
    private Integer egeNumber;

    @NotBlank(message = "Укажите название")
    @Size(max = 300, message = "Не длиннее 300 символов")
    private String title;

    @NotBlank(message = "Условие не может быть пустым")
    private String statementMd;

    @NotNull(message = "Укажите сложность")
    @Min(value = 1, message = "От 1 до 5")
    @Max(value = 5, message = "От 1 до 5")
    private Integer difficulty = 3;

    @NotNull(message = "Укажите баллы")
    @Min(value = 0, message = "Не меньше 0")
    @Max(value = 100, message = "Не больше 100")
    private Integer maxPoints = 1;

    @NotNull
    private Scoring scoring = Scoring.ALL_OR_NOTHING;

    @NotNull(message = "Укажите число строк")
    @Min(value = 1, message = "От 1 до 50")
    @Max(value = 50, message = "От 1 до 50")
    private Integer answerRows = 1;

    @NotNull(message = "Укажите число столбцов")
    @Min(value = 1, message = "От 1 до 10")
    @Max(value = 10, message = "От 1 до 10")
    private Integer answerCols = 1;

    @NotBlank(message = "Укажите правильный ответ")
    private String correctAnswer;

    /** Галочка «Добавить разбор»: без неё разбор не сохраняется. */
    private boolean solutionEnabled;

    private String solutionMd;

    @Size(max = 200, message = "Не длиннее 200 символов")
    private String topic;

    public static TaskForm from(Task task) {
        TaskForm form = new TaskForm();
        form.egeNumber = task.getEgeNumber();
        form.title = task.getTitle();
        form.statementMd = task.getStatementMd();
        form.difficulty = task.getDifficulty();
        form.maxPoints = task.getMaxPoints();
        form.scoring = task.getScoring();
        form.answerRows = task.getAnswerRows();
        form.answerCols = task.getAnswerCols();
        form.correctAnswer = AnswerNormalizer.format(task.getCorrectAnswer());
        form.solutionMd = task.getSolutionMd();
        form.solutionEnabled = task.getSolutionMd() != null;
        form.topic = task.getTopic();
        return form;
    }

    public void applyTo(Task task) {
        task.setEgeNumber(egeNumber);
        task.setTitle(title.trim());
        task.setStatementMd(statementMd);
        task.setDifficulty(difficulty);
        task.setMaxPoints(maxPoints);
        task.setScoring(scoring);
        task.setAnswerRows(answerRows);
        task.setAnswerCols(answerCols);
        task.setCorrectAnswer(AnswerNormalizer.parse(correctAnswer));
        task.setSolutionMd(solutionEnabled ? blankToNull(solutionMd) : null);
        task.setTopic(blankToNull(topic));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Integer getEgeNumber() {
        return egeNumber;
    }

    public void setEgeNumber(Integer egeNumber) {
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

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Integer maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Scoring getScoring() {
        return scoring;
    }

    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }

    public Integer getAnswerRows() {
        return answerRows;
    }

    public void setAnswerRows(Integer answerRows) {
        this.answerRows = answerRows;
    }

    public Integer getAnswerCols() {
        return answerCols;
    }

    public void setAnswerCols(Integer answerCols) {
        this.answerCols = answerCols;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public boolean isSolutionEnabled() {
        return solutionEnabled;
    }

    public void setSolutionEnabled(boolean solutionEnabled) {
        this.solutionEnabled = solutionEnabled;
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
}
