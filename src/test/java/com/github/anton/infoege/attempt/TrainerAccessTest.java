package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.task.FileKind;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskFile;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Доступ к чужим попыткам, назначениям и файлам (IDOR). Чужое не находится: ответ 404.
 */
class TrainerAccessTest extends AttemptTestSupport {

    User owner;
    User intruder;
    Task task;
    Task foreignTask;
    TaskFile ownFile;
    TaskFile foreignFile;
    Assignment assignment;
    Attempt attempt;

    @BeforeEach
    void setUp() {
        owner = student();
        intruder = student();
        task = task(11, 1, 1, "1");
        foreignTask = task(12, 1, 1, "1");
        taskService.addFiles(task.getId(), List.of(new MockMultipartFile("files", "11.txt", "x", "own".getBytes())), FileKind.ATTACHMENT);
        taskService.addFiles(foreignTask.getId(), List.of(new MockMultipartFile("files", "12.txt", "x", "other".getBytes())), FileKind.ATTACHMENT);
        ownFile = taskService.files(task.getId()).get(0);
        foreignFile = taskService.files(foreignTask.getId()).get(0);
        assignment = assign(variant(60, task), owner);
        attempt = attemptService.start(assignment.getId(), owner.getId());
        // У нарушителя своя попытка на другой работе: доступ к ней не должен давать доступ к чужой.
        attemptService.start(assign(variant(60, foreignTask), intruder).getId(), intruder.getId());
    }

    @Test
    void ownerHasAccess() throws Exception {
        mvc.perform(get("/trainer/attempts/{id}", attempt.getId()).with(asStudent(owner.getLogin())))
                .andExpect(status().isOk());
        mvc.perform(get("/trainer/attempts/{id}/files/{f}", attempt.getId(), ownFile.getId()).with(asStudent(owner.getLogin())))
                .andExpect(status().isOk())
                .andExpect(content().bytes("own".getBytes()));
    }

    @Test
    void fileOfTaskOutsideAttemptIsNotServed() throws Exception {
        mvc.perform(get("/trainer/attempts/{id}/files/{f}", attempt.getId(), foreignFile.getId()).with(asStudent(owner.getLogin())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/trainer/attempts/{id}/files/{f}", attempt.getId(), 999999L).with(asStudent(owner.getLogin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherStudentCannotTouchAttempt() throws Exception {
        String who = intruder.getLogin();
        mvc.perform(get("/trainer/attempts/{id}", attempt.getId()).with(asStudent(who)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/trainer/attempts/{id}/files/{f}", attempt.getId(), ownFile.getId()).with(asStudent(who)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/trainer/attempts/{id}/answers", attempt.getId()).with(asStudent(who)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"hack\"]]")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/trainer/attempts/{id}/heartbeat", attempt.getId()).with(asStudent(who)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":" + task.getId() + ",\"seconds\":10}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/trainer/attempts/{id}/finish", attempt.getId()).with(asStudent(who)).with(csrf()))
                .andExpect(status().isNotFound());

        Attempt untouched = attemptRepository.findById(attempt.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attempt.getId(), task.getId()).orElseThrow();
        assertThat(answer.getAnswer()).isNull();
        assertThat(answer.getTimeSpentSeconds()).isZero();
    }

    @Test
    void otherStudentCannotOpenOrStartAssignment() throws Exception {
        Assignment notStarted = assign(variant(60, task), owner);

        mvc.perform(get("/student/assignments/{id}", notStarted.getId()).with(asStudent(intruder.getLogin())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/student/assignments/{id}/start", notStarted.getId()).with(asStudent(intruder.getLogin())).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(attemptRepository.findByAssignmentId(notStarted.getId())).isEmpty();
    }

    @Test
    void answerForTaskOutsideAttemptIsRejected() throws Exception {
        mvc.perform(post("/trainer/attempts/{id}/answers", attempt.getId()).with(asStudent(owner.getLogin())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(foreignTask.getId(), "[[\"1\"]]")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherAndAnonymousCannotUseTrainer() throws Exception {
        mvc.perform(get("/trainer/attempts/{id}", attempt.getId()).with(asTeacher()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/trainer/attempts/{id}", attempt.getId()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/trainer/attempts/{id}/files/{f}", attempt.getId(), ownFile.getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void trainerApiRequiresCsrf() throws Exception {
        mvc.perform(post("/trainer/attempts/{id}/answers", attempt.getId()).with(asStudent(owner.getLogin()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(task.getId(), "[[\"1\"]]")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/trainer/attempts/{id}/finish", attempt.getId()).with(asStudent(owner.getLogin())))
                .andExpect(status().isForbidden());
    }
}
