package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskForm;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainerFlowTest extends AttemptTestSupport {

    /** Критерий готовности этапа: вариант проходится от начала до конца, ответы сохраняются и восстанавливаются. */
    @Test
    void variantFromStartToFinish() throws Exception {
        User student = student();
        Task t1 = task(1, 1, 1, "42");
        Task t27 = task(27, 2, 2, "11 22\n33 44");
        TaskSet set = variant(235, t27, t1);
        Assignment assignment = assign(set, student);

        String location = mvc.perform(post("/student/assignments/{id}/start", assignment.getId())
                        .with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Attempt attempt = attemptRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertThat(location).isEqualTo("/trainer/attempts/" + attempt.getId());
        assertThat(attempt.getExpiresAt()).isCloseTo(attempt.getStartedAt().plus(Duration.ofMinutes(235)), within(Duration.ofSeconds(1)));

        String page = mvc.perform(get(location).with(asStudent(student.getLogin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page)
                .contains(t1.getStatementMd(), t27.getStatementMd())
                .contains("name=\"cell-1-1\"")
                .doesNotContain("Разбор-секрет", "ПЯТЬ", "ШЕСТЬ")
                .doesNotContain("site-header");
        assertThat(page.indexOf(t1.getStatementMd())).as("вариант идёт по номерам").isLessThan(page.indexOf(t27.getStatementMd()));

        mvc.perform(post(location + "/answers").with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(t27.getId(), "[[\" 11 \",\"22\"],[\"\",\"5\"]]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.remainingSeconds").isNumber())
                .andExpect(jsonPath("$.answerSnapshot").doesNotExist());
        mvc.perform(post(location + "/heartbeat").with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + t1.getId() + ",\"seconds\":5}"))
                .andExpect(status().isOk());

        // Повторный вход: та же попытка, сохранённый ответ на месте, статусы восстановлены.
        mvc.perform(post("/student/assignments/{id}/start", assignment.getId())
                        .with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(redirectedUrl(location));
        String reloaded = mvc.perform(get(location).with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reloaded).contains("value=\"11\"", "value=\"22\"", "value=\"5\"")
                .contains("class=\"task-btn status-opened\"", "class=\"task-btn status-saved\"");
        assertThat(attemptRepository.count()).isGreaterThan(0);
        assertThat(attemptRepository.findByAssignmentIdIn(List.of(assignment.getId()))).hasSize(1);

        AttemptAnswer saved = answerRepository.findByAttemptIdAndTaskId(attempt.getId(), t27.getId()).orElseThrow();
        assertThat(saved.getAnswer()).isEqualTo(List.of(List.of("11", "22"), List.of("", "5")));
        assertThat(saved.getAnswerChanges()).isEqualTo(1);
        assertThat(saved.getLastSavedAt()).isNotNull();

        mvc.perform(post(location + "/finish").with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.redirect").value("/student/assignments/" + assignment.getId()));

        Attempt finished = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(AttemptStatus.FINISHED);
        assertThat(finished.getFinishedAt()).isAfterOrEqualTo(finished.getStartedAt());

        mvc.perform(post(location + "/answers").with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(t1.getId(), "[[\"1\"]]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("FINISHED"));
        mvc.perform(get(location).with(asStudent(student.getLogin())))
                .andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));
        mvc.perform(get("/student").with(asStudent(student.getLogin())))
                .andExpect(content().string(containsString("завершено")));
    }

    @Test
    void correctAnswerIsSnapshottedAtStart() {
        User student = student();
        Task task = task(5, 1, 1, "100");
        Assignment assignment = assign(variant(60, task), student);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());

        TaskForm edit = TaskForm.from(taskService.get(task.getId()));
        edit.setCorrectAnswer("200");
        edit.setMaxPoints(7);
        taskService.update(task.getId(), edit);

        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attempt.getId(), task.getId()).orElseThrow();
        assertThat(answer.getAnswerSnapshot()).isEqualTo(List.of(List.of("100")));
        assertThat(answer.getPointsSnapshot()).isEqualTo(2);
    }

    @Test
    void attemptEndsAtTimeLimitOrDeadlineWhicheverIsFirst() {
        Instant start = Instant.parse("2026-01-10T10:00:00Z");

        assertThat(AttemptService.expiresAt(start, 60, null)).isEqualTo(start.plus(Duration.ofMinutes(60)));
        assertThat(AttemptService.expiresAt(start, 60, start.plus(Duration.ofMinutes(30))))
                .isEqualTo(start.plus(Duration.ofMinutes(30)));
        assertThat(AttemptService.expiresAt(start, 60, start.plus(Duration.ofDays(1))))
                .isEqualTo(start.plus(Duration.ofMinutes(60)));
        assertThat(AttemptService.expiresAt(start, null, null)).isNull();
    }

    @Test
    void expiredAttemptIsClosedOnRequest() throws Exception {
        User student = student();
        Task task = task(2, 1, 1, "1");
        Assignment assignment = assign(variant(30, task), student);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        jdbc.update("update attempt set expires_at = now() - interval '1 minute' where id = ?", attempt.getId());

        mvc.perform(post("/trainer/attempts/{id}/answers", attempt.getId()).with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"1\"]]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(closed.getFinishedAt()).isEqualTo(closed.getExpiresAt());
        assertThat(answerRepository.findByAttemptIdAndTaskId(attempt.getId(), task.getId()).orElseThrow().getAnswer()).isNull();
    }

    @Test
    void saveWithinGracePeriodIsAccepted() throws Exception {
        User student = student();
        Task task = task(2, 1, 1, "1");
        Attempt attempt = attemptService.start(assign(variant(30, task), student).getId(), student.getId());
        jdbc.update("update attempt set expires_at = now() - interval '2 seconds' where id = ?", attempt.getId());

        mvc.perform(post("/trainer/attempts/{id}/answers", attempt.getId()).with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"7\"]]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingSeconds").value(0));
    }

    @Test
    void schedulerClosesOverdueAttempts() {
        User student = student();
        Task task = task(3, 1, 1, "1");
        Attempt overdue = attemptService.start(assign(variant(30, task), student).getId(), student.getId());
        Attempt running = attemptService.start(assign(variant(30, task), student).getId(), student.getId());
        Attempt unlimited = attemptService.start(assign(variant(null, task), student).getId(), student.getId());
        jdbc.update("update attempt set expires_at = now() - interval '1 minute' where id = ?", overdue.getId());

        new AttemptExpiryScheduler(attemptService).expireOverdue();

        assertThat(attemptRepository.findById(overdue.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attemptRepository.findById(overdue.getId()).orElseThrow().getFinishedAt())
                .isEqualTo(attemptRepository.findById(overdue.getId()).orElseThrow().getExpiresAt());
        assertThat(attemptRepository.findById(running.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attemptRepository.findById(unlimited.getId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(attemptRepository.findById(unlimited.getId()).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void cannotStartAfterDeadline() throws Exception {
        User student = student();
        Assignment assignment = assign(variant(30, task(4, 1, 1, "1")), student);
        jdbc.update("update assignment set deadline_at = now() - interval '1 minute' where id = ?", assignment.getId());

        mvc.perform(post("/student/assignments/{id}/start", assignment.getId()).with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(redirectedUrl("/student/assignments/" + assignment.getId()));

        assertThat(attemptRepository.findByAssignmentId(assignment.getId())).isEmpty();
        mvc.perform(get("/student").with(asStudent(student.getLogin())))
                .andExpect(content().string(containsString("просрочено")));
    }

    @Test
    void heartbeatCreditIsLimitedByServer() throws Exception {
        User student = student();
        Task task = task(6, 1, 1, "1");
        Attempt attempt = attemptService.start(assign(variant(60, task), student).getId(), student.getId());
        String url = "/trainer/attempts/" + attempt.getId() + "/heartbeat";
        jdbc.update("update attempt set last_heartbeat_at = now() - interval '60 seconds' where id = ?", attempt.getId());

        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + task.getId() + ",\"seconds\":1000}"))
                .andExpect(status().isOk());
        assertThat(timeSpent(attempt, task)).isEqualTo(AttemptService.MAX_HEARTBEAT_SECONDS);

        // Сразу же второй запрос: с прошлого heartbeat прошло ~0 секунд, засчитывать нечего.
        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + task.getId() + ",\"seconds\":15}"));
        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + task.getId() + ",\"seconds\":-50}"));
        assertThat(timeSpent(attempt, task)).isBetween(AttemptService.MAX_HEARTBEAT_SECONDS, AttemptService.MAX_HEARTBEAT_SECONDS + 1);

        jdbc.update("update attempt set last_heartbeat_at = now() - interval '60 seconds' where id = ?", attempt.getId());
        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + task.getId() + ",\"seconds\":7}"));
        assertThat(timeSpent(attempt, task)).isBetween(27, 28);
        assertThat(answerRepository.findByAttemptIdAndTaskId(attempt.getId(), task.getId()).orElseThrow().getFirstOpenedAt())
                .isNotNull();
    }

    @Test
    void validatesAnswerShapeAndCountsRealChanges() throws Exception {
        User student = student();
        Task task = task(7, 1, 2, "1 2");
        Attempt attempt = attemptService.start(assign(variant(60, task), student).getId(), student.getId());
        String url = "/trainer/attempts/" + attempt.getId() + "/answers";

        for (String bad : List.of("[[\"1\"]]", "[[\"1\",\"2\"],[\"3\",\"4\"]]", "[[\"" + "x".repeat(101) + "\",\"1\"]]")) {
            mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), bad)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"1\",\"2\"]]")));
        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\" 1\",\"2 \"]]")));
        assertThat(answer(attempt, task).getAnswerChanges()).isEqualTo(1);

        mvc.perform(post(url).with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"\",\" \"]]")));
        assertThat(answer(attempt, task).getAnswer()).isNull();
        assertThat(answer(attempt, task).getAnswerChanges()).isEqualTo(2);
    }

    @Test
    void startedAssignmentCannotBeUnassigned() throws Exception {
        User student = student();
        TaskSet set = variant(60, task(8, 1, 1, "1"));
        Assignment assignment = assign(set, student);
        attemptService.start(assignment.getId(), student.getId());

        assertThat(assignmentService.delete(set.getId(), assignment.getId())).isFalse();
    }

    @Test
    void studentPagesShowStatuses() throws Exception {
        User student = student();
        TaskSet set = variant(60, task(9, 1, 1, "1"));
        Assignment assignment = assign(set, student);

        mvc.perform(get("/student/assignments/{id}", assignment.getId()).with(asStudent(student.getLogin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Начать")));
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        mvc.perform(get("/student/assignments/{id}", assignment.getId()).with(asStudent(student.getLogin())))
                .andExpect(content().string(containsString("/trainer/attempts/" + attempt.getId())))
                .andExpect(content().string(containsString("в процессе")));
    }

    private int timeSpent(Attempt attempt, Task task) {
        return answer(attempt, task).getTimeSpentSeconds();
    }

    private AttemptAnswer answer(Attempt attempt, Task task) {
        return answerRepository.findByAttemptIdAndTaskId(attempt.getId(), task.getId()).orElseThrow();
    }
}
