package com.github.anton.infoege.result;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.attempt.Attempt;
import com.github.anton.infoege.attempt.AttemptRepository;
import com.github.anton.infoege.attempt.AttemptService;
import com.github.anton.infoege.attempt.AttemptStatus;
import com.github.anton.infoege.attempt.WorkStatus;
import com.github.anton.infoege.task.Scoring;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskForm;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetForm;
import com.github.anton.infoege.taskset.TaskSetService;
import com.github.anton.infoege.taskset.TaskSetType;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultFlowTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TaskService taskService;

    @Autowired
    TaskSetService taskSetService;

    @Autowired
    AssignmentService assignmentService;

    @Autowired
    AttemptService attemptService;

    @Autowired
    AttemptRepository attemptRepository;

    @Autowired
    ResultService resultService;

    @Autowired
    JdbcTemplate jdbc;

    /** Критерий готовности этапа: преподаватель видит, кто решил работу, с каким результатом и за какое время. */
    @Test
    void teacherSeesWhoSolvedWithScoreAndTime() throws Exception {
        User solved = student("Решивший");
        User idle = student("Не начавший");
        Task t1 = task(1, "42", Scoring.ALL_OR_NOTHING, 1, 1);
        Task t27 = task(27, "10\n20", Scoring.PER_ROW, 2, 1);
        TaskSet set = set(t1, t27);
        Assignment a = assign(set, solved, null, ShowResultsMode.AFTER_FINISH);
        assign(set, idle, null, ShowResultsMode.AFTER_FINISH);

        Attempt attempt = attemptService.start(a.getId(), solved.getId());
        attemptService.saveAnswer(attempt.getId(), solved.getId(), t1.getId(), List.of(List.of(" 42 ")));
        attemptService.saveAnswer(attempt.getId(), solved.getId(), t27.getId(), List.of(List.of("10"), List.of("99")));
        jdbc.update("update attempt set started_at = now() - interval '17 minutes' where id = ?", attempt.getId());
        jdbc.update("update attempt_answer set time_spent_seconds = 125 where attempt_id = ? and task_id = ?", attempt.getId(), t27.getId());
        attemptService.finish(attempt.getId(), solved.getId());

        Attempt finished = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(AttemptStatus.FINISHED);
        assertThat(finished.getScore()).isEqualTo(2); // 1 за первое + 1 из 2 по строкам

        mvc.perform(get("/teacher/sets/{id}", set.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Решивший")))
                .andExpect(content().string(containsString("завершено")))
                .andExpect(content().string(containsString("<strong>2</strong> / <span>3</span>")))
                .andExpect(content().string(containsString("0:17:")))
                .andExpect(content().string(containsString("Не начавший")))
                .andExpect(content().string(containsString("не начато")));

        String detail = mvc.perform(get("/teacher/assignments/{id}", a.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(detail).contains("99", "0:02:05", "row-correct", "row-wrong");

        String csv = mvc.perform(get("/teacher/results.csv").param("setId", set.getId().toString()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Ученик;Логин;Работа")
                .contains("Решивший;" + solved.getLogin() + ";" + set.getTitle() + ";подборка;завершено")
                .contains(";2;;2;3;")
                .contains("Не начавший;" + idle.getLogin());

        String tasksCsv = mvc.perform(get("/teacher/results/tasks.csv").param("studentId", solved.getId().toString()).with(asTeacher()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(tasksCsv).contains(";10 / 99;10 / 20;нет;1;2;125;");
    }

    @Test
    void expiredAttemptIsGradedToo() {
        User s = student("Опоздавший");
        Task t = task(3, "7", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment a = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Attempt attempt = attemptService.start(a.getId(), s.getId());
        attemptService.saveAnswer(attempt.getId(), s.getId(), t.getId(), List.of(List.of("7")));
        jdbc.update("update attempt set expires_at = now() - interval '1 minute' where id = ?", attempt.getId());

        attemptService.expireOverdue();

        Attempt expired = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(expired.getScore()).isEqualTo(1);
    }

    @Test
    void manualScoreOverridesAutomaticOne() throws Exception {
        User s = student("Корректировка");
        Task t = task(4, "1", Scoring.ALL_OR_NOTHING, 3, 1);
        Assignment a = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Attempt attempt = attemptService.start(a.getId(), s.getId());

        mvc.perform(post("/teacher/assignments/{id}/score", a.getId()).param("manualScore", "2").with(asTeacher()).with(csrf()))
                .andExpect(flash().attribute("error", "Работа ещё не завершена"));

        attemptService.finish(attempt.getId(), s.getId());
        mvc.perform(post("/teacher/assignments/{id}/score", a.getId()).param("manualScore", "2")
                        .param("comment", "Засчитано за верный ход решения").with(asTeacher()).with(csrf()))
                .andExpect(flash().attributeExists("message"));
        Attempt adjusted = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(adjusted.getScore()).isZero();
        assertThat(adjusted.getFinalScore()).isEqualTo(2);
        assertThat(adjusted.getManualComment()).isEqualTo("Засчитано за верный ход решения");

        mvc.perform(post("/teacher/assignments/{id}/score", a.getId()).param("manualScore", "4").with(asTeacher()).with(csrf()))
                .andExpect(flash().attribute("error", "Балл должен быть от 0 до 3"));

        mvc.perform(post("/teacher/assignments/{id}/score", a.getId()).param("manualScore", "").with(asTeacher()).with(csrf()));
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getFinalScore()).isZero();
    }

    @Test
    void studentSeesResultsAfterFinishOnly() throws Exception {
        User s = student("После завершения");
        Task t = task(5, "ОТВЕТЯЧЕЙКА", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment a = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Attempt attempt = attemptService.start(a.getId(), s.getId());

        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(status().isNotFound());

        attemptService.saveAnswer(attempt.getId(), s.getId(), t.getId(), List.of(List.of("ответячейка")));
        attemptService.finish(attempt.getId(), s.getId());

        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>1</strong> из <span>1</span>")))
                .andExpect(content().string(containsString("ОТВЕТЯЧЕЙКА")))
                .andExpect(content().string(containsString("Разбор задания 5")));
        mvc.perform(get("/student/assignments/{id}", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(content().string(containsString("/student/assignments/" + a.getId() + "/result")));

        User other = student("Чужой");
        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(other.getLogin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherOnlyResultsAreHiddenFromStudent() throws Exception {
        User s = student("Только преподаватель");
        Task t = task(6, "СЕКРЕТНЫЙОТВЕТ", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment a = assign(set(t), s, null, ShowResultsMode.TEACHER_ONLY);
        attemptService.finish(attemptService.start(a.getId(), s.getId()).getId(), s.getId());

        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/student/assignments/{id}", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(content().string(containsString("Результаты увидит преподаватель")))
                .andExpect(content().string(not(containsString("СЕКРЕТНЫЙОТВЕТ"))));
    }

    @Test
    void afterDeadlineResultsOpenWhenDeadlinePasses() throws Exception {
        User s = student("После срока");
        Task t = task(7, "1", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment a = assign(set(t), s, Instant.now().plusSeconds(3600), ShowResultsMode.AFTER_DEADLINE);
        attemptService.finish(attemptService.start(a.getId(), s.getId()).getId(), s.getId());

        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(status().isNotFound());

        jdbc.update("update assignment set deadline_at = now() - interval '1 minute' where id = ?", a.getId());
        mvc.perform(get("/student/assignments/{id}/result", a.getId()).with(asStudent(s.getLogin())))
                .andExpect(status().isOk());
    }

    @Test
    void statusesForTeacher() {
        User s = student("Статусы");
        Task t = task(8, "1", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment notStarted = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Assignment inProgress = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Assignment finished = assign(set(t), s, null, ShowResultsMode.AFTER_FINISH);
        Assignment overdue = assign(set(t), s, Instant.now().plusSeconds(60), ShowResultsMode.AFTER_FINISH);
        attemptService.start(inProgress.getId(), s.getId());
        attemptService.finish(attemptService.start(finished.getId(), s.getId()).getId(), s.getId());
        jdbc.update("update assignment set deadline_at = now() - interval '1 minute' where id = ?", overdue.getId());

        var byId = resultService.results(null, s.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.assignment().getId(), ResultService.AssignmentResult::status));
        assertThat(byId.get(notStarted.getId())).isEqualTo(WorkStatus.NOT_STARTED);
        assertThat(byId.get(inProgress.getId())).isEqualTo(WorkStatus.IN_PROGRESS);
        assertThat(byId.get(finished.getId())).isEqualTo(WorkStatus.FINISHED);
        assertThat(byId.get(overdue.getId())).isEqualTo(WorkStatus.OVERDUE);
    }

    @Test
    void studentSummaryAndResultPagesRender() throws Exception {
        User s = student("Сводка");
        Task t1 = task(9, "1", Scoring.ALL_OR_NOTHING, 1, 1);
        Task t2 = task(9, "2", Scoring.ALL_OR_NOTHING, 1, 1);
        Assignment a = assign(set(t1, t2), s, null, ShowResultsMode.AFTER_FINISH);
        Attempt attempt = attemptService.start(a.getId(), s.getId());
        attemptService.saveAnswer(attempt.getId(), s.getId(), t1.getId(), List.of(List.of("1")));
        attemptService.finish(attempt.getId(), s.getId());

        var summary = resultService.studentSummary(s.getId());
        assertThat(summary.closed()).isEqualTo(1);
        assertThat(summary.points()).isEqualTo(1);
        assertThat(summary.maxPoints()).isEqualTo(2);
        assertThat(summary.byNumber()).singleElement()
                .satisfies(n -> assertThat(n.correct()).isEqualTo(1))
                .satisfies(n -> assertThat(n.tasks()).isEqualTo(2));

        mvc.perform(get("/teacher/students/{id}", s.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 из 2")))
                .andExpect(content().string(containsString("<span>50</span>%")));
        mvc.perform(get("/teacher/results").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/results").param("studentId", s.getId().toString()).with(asTeacher()))
                .andExpect(content().string(containsString("Сводка")));
    }

    @Test
    void resultsAreTeacherOnly() throws Exception {
        User s = student("Любопытный");
        for (String url : List.of("/teacher/results", "/teacher/results.csv", "/teacher/results/tasks.csv")) {
            mvc.perform(get(url).with(asStudent(s.getLogin()))).andExpect(status().isForbidden());
        }
    }

    private User student(String name) {
        return userRepository.save(new User(Role.STUDENT, name, "r-" + UUID.randomUUID().toString().substring(0, 8), "{noop}x"));
    }

    private Task task(int egeNumber, String correct, Scoring scoring, int maxPoints, int cols) {
        TaskForm form = new TaskForm();
        form.setEgeNumber(egeNumber);
        form.setTitle("Задание " + egeNumber);
        form.setStatementMd("Условие " + egeNumber);
        form.setSolutionMd("Разбор задания " + egeNumber);
        form.setCorrectAnswer(correct);
        form.setAnswerRows((int) correct.lines().count());
        form.setAnswerCols(cols);
        form.setScoring(scoring);
        form.setMaxPoints(maxPoints);
        return taskService.create(form);
    }

    private TaskSet set(Task... tasks) {
        TaskSetForm form = new TaskSetForm();
        form.setType(TaskSetType.COLLECTION);
        form.setTitle("Работа " + UUID.randomUUID().toString().substring(0, 8));
        TaskSet set = taskSetService.create(form);
        taskSetService.addTasks(set.getId(), java.util.Arrays.stream(tasks).map(Task::getId).toList());
        return set;
    }

    private Assignment assign(TaskSet set, User student, Instant deadline, ShowResultsMode mode) {
        assignmentService.assign(set.getId(), List.of(student.getId()), deadline, mode);
        return assignmentService.forStudent(student.getId()).stream()
                .filter(x -> x.getTaskSet().getId().equals(set.getId())).findFirst().orElseThrow();
    }
}
