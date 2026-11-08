package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.result.ResultService;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Закончить потом»: пауза останавливает время, продолжение сдвигает конец попытки; без разрешения паузы нет. */
class PauseTest extends AttemptTestSupport {

    @Autowired
    ResultService resultService;

    @Test
    void pauseStopsTimeAndResumeShiftsEnd() throws Exception {
        User student = student();
        Task t = task(1, 1, 1, "42");
        Assignment assignment = assignPausable(variant(60, t), student, null);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        String api = "/trainer/attempts/" + attempt.getId();
        Instant expiresBefore = attemptRepository.findById(attempt.getId()).orElseThrow().getExpiresAt();

        String page = mvc.perform(get(api).with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Закончить потом");

        mvc.perform(post(api + "/pause").with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/student/assignments/" + assignment.getId()));
        // На паузе ответы не принимаются (другая вкладка увидит, что работа на паузе).
        mvc.perform(post(api + "/answers").with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(t.getId(), "[[\"42\"]]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // Пауза длилась 40 минут — больше, чем осталось бы без неё. Попытка не истекает.
        jdbc.update("update attempt set paused_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(40))), attempt.getId());
        attemptService.expireOverdue();
        Attempt paused = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(paused.isInProgress()).isTrue();
        assertThat(WorkStatus.of(assignment, paused, Instant.now())).isEqualTo(WorkStatus.PAUSED);

        mvc.perform(get(api).with(asStudent(student.getLogin()))).andExpect(status().isOk());
        Attempt resumed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(resumed.isPaused()).isFalse();
        assertThat(resumed.getPausedSeconds()).isBetween(40L * 60 - 5, 40L * 60 + 5);
        assertThat(resumed.getExpiresAt()).isCloseTo(expiresBefore.plus(Duration.ofMinutes(40)), within(Duration.ofSeconds(5)));
        assertThat(resumed.totalSeconds(Instant.now())).isLessThan(60);

        mvc.perform(post(api + "/answers").with(asStudent(student.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(t.getId(), "[[\"42\"]]")))
                .andExpect(status().isOk());
    }

    @Test
    void pausedAttemptClosesAtDeadline() {
        User student = student();
        Task t = task(1, 1, 1, "42");
        Assignment assignment = assignPausable(variant(60, t), student, Instant.now().plus(Duration.ofHours(1)));
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        attemptService.pause(attempt.getId(), student.getId());

        // Срок прошёл, пока работа на паузе: пауза срок не продлевает.
        jdbc.update("update assignment set deadline_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))), assignment.getId());
        assertThat(attemptService.expireOverdue()).isEqualTo(1);
        Attempt closed = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(closed.isPaused()).isFalse();
        assertThat(closed.getScore()).isZero();
    }

    @Test
    void pauseIsRejectedWhenNotAllowed() throws Exception {
        User student = student();
        Task t = task(1, 1, 1, "42");
        Assignment assignment = assign(variant(60, t), student);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        String api = "/trainer/attempts/" + attempt.getId();

        String page = mvc.perform(get(api).with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("pause-btn");
        mvc.perform(post(api + "/pause").with(asStudent(student.getLogin())).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().isPaused()).isFalse();
    }

    @Test
    void statsCountAnsweredAndCorrectTasks() {
        User student = student();
        Task t1 = task(1, 1, 1, "1");
        Task t2 = task(2, 1, 1, "2");
        Task t3 = task(3, 1, 1, "3");
        Assignment assignment = assign(variant(null, t1, t2, t3), student);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        attemptService.saveAnswer(attempt.getId(), student.getId(), t1.getId(), List.of(List.of("1")));
        attemptService.saveAnswer(attempt.getId(), student.getId(), t2.getId(), List.of(List.of("5")));

        ResultService.TaskStats inProgress = resultService.result(assignment.getId()).stats();
        assertThat(inProgress.answered()).isEqualTo(2);
        assertThat(inProgress.correct()).isZero();

        attemptService.finish(attempt.getId(), student.getId());
        ResultService.TaskStats stats = resultService.statsByStudent().get(student.getId());
        assertThat(stats).isEqualTo(new ResultService.TaskStats(3, 2, 1));
        assertThat(stats.correctPercent()).isEqualTo(33);
        assertThat(resultService.studentSummary(student.getId()).stats()).isEqualTo(stats);
    }

    private Assignment assignPausable(TaskSet set, User student, Instant deadline) {
        assignmentService.assign(set.getId(), List.of(student.getId()), deadline, ShowResultsMode.AFTER_FINISH, true);
        return assignmentService.forStudent(student.getId()).stream()
                .filter(a -> a.getTaskSet().getId().equals(set.getId())).findFirst().orElseThrow();
    }
}
