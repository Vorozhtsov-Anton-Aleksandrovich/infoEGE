package com.github.anton.infoege.course;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.course.CourseProgressService.Progress;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseProgressTest extends IntegrationTest {

    private static final String COURSE = "python-ege";

    @Autowired
    MockMvc mvc;

    @Autowired
    CourseProgressRepository repository;

    private String newLogin() {
        return "cp-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private User student(String login) {
        asStudent(login);
        return userRepository.findByLogin(login).orElseThrow();
    }

    private void record(User student, String taskId, boolean solved, String code, Instant at) {
        repository.upsertStatus(student.getId(), COURSE, taskId, code, solved, at);
        repository.saveAttempt(student.getId(), COURSE, taskId, code, solved ? 1 : 0, 1, solved, at);
    }

    @Test
    void progressLevelsFollowAnalyticsColors() {
        assertThat(new Progress(0, 5).level()).isEqualTo("none");
        assertThat(new Progress(1, 5).level()).isEqualTo("low");
        assertThat(new Progress(2, 5).level()).isEqualTo("mid");
        assertThat(new Progress(4, 5).level()).isEqualTo("good");
        assertThat(new Progress(5, 5).complete()).isTrue();
        assertThat(new Progress(0, 0).percent()).isZero();
    }

    @Test
    void newStudentSeesStartButton() throws Exception {
        RequestPostProcessor student = asStudent(newLogin());
        mvc.perform(get("/student").with(student))
                .andExpect(content().string(containsString("решено 0 из 72")))
                .andExpect(content().string(containsString("Начать курс")))
                .andExpect(content().string(containsString("/courses/python-ege/01-first-program")));
    }

    @Test
    void studentSeesProgressAndContinueLink() throws Exception {
        String login = newLogin();
        User s = student(login);
        Instant now = Instant.now();
        record(s, "py01-1", true, "print('Hello, World!')", now.minusSeconds(60));
        record(s, "py12-1", true, "a", now.minusSeconds(30));
        record(s, "py12-2", true, "b", now.minusSeconds(20));
        record(s, "py05-5", false, "плохой код", now);         // последняя проверка — в уроке 5

        mvc.perform(get("/student").with(asStudent(login)))
                .andExpect(content().string(containsString("решено 3 из 72")))
                .andExpect(content().string(containsString("Продолжить")))
                .andExpect(content().string(containsString("href=\"/courses/python-ege/05-for-loop\"")))
                .andExpect(content().string(containsString("последний урок: 5. Цикл for")));

        mvc.perform(get("/courses/python-ege").with(asStudent(login)))
                .andExpect(content().string(containsString("решено задач 1/5")))     // урок 1
                .andExpect(content().string(containsString("решено задач 2/2")))     // урок 12 решён целиком
                .andExpect(content().string(matchesPattern("(?s).*class=\"lesson-number\">✓</span>.*")));

        mvc.perform(get("/courses").with(asStudent(login)))
                .andExpect(content().string(containsString("решено 3 из 72")));
    }

    @Test
    void teacherSeesTableAndStudentTasks() throws Exception {
        String login = newLogin();
        User s = student(login);
        record(s, "py01-1", true, "print('Hello, World!')", Instant.now().minusSeconds(10));
        record(s, "py01-2", false, "print('черновик')", Instant.now());

        mvc.perform(get("/teacher/courses/python-ege").with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ученик " + login)))
                .andExpect(content().string(containsString(
                        "/teacher/courses/python-ege/students/" + s.getId())))
                .andExpect(content().string(matchesPattern("(?s).*class=\"cell-low\"[^>]*>1/5</td>.*")));

        mvc.perform(get("/teacher/courses/python-ege/students/" + s.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Решено 1 из 72 задач.")))
                .andExpect(content().string(containsString(">решена</span>")))
                .andExpect(content().string(containsString("print(&#39;черновик&#39;)")))
                .andExpect(content().string(containsString("проверок: 1")));

        mvc.perform(get("/teacher/students/" + s.getId()).with(asTeacher()))
                .andExpect(content().string(containsString("/teacher/courses/python-ege/students/" + s.getId())));
    }

    @Test
    void teacherPagesAreClosedForStudentsAndCheckIds() throws Exception {
        mvc.perform(get("/teacher/courses/python-ege").with(asStudent(newLogin()))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/courses/nope").with(asTeacher())).andExpect(status().isNotFound());
        mvc.perform(get("/teacher/courses/python-ege/students/999999").with(asTeacher())).andExpect(status().isNotFound());
        Long teacherId = userRepository.findByLogin(TEACHER_LOGIN).orElseThrow().getId();
        mvc.perform(get("/teacher/courses/python-ege/students/" + teacherId).with(asTeacher()))
                .andExpect(status().isNotFound());
    }

    @Test
    void coursePageShowsProgressButtonOnlyToTeacher() throws Exception {
        mvc.perform(get("/courses/python-ege").with(asTeacher()))
                .andExpect(content().string(containsString("href=\"/teacher/courses/python-ege\"")));
        mvc.perform(get("/courses/python-ege").with(asStudent(newLogin())))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/teacher/courses/"))));
    }
}
