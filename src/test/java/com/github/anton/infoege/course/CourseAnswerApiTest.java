package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Задачи без кода курса «Основы информатики»: ответ сверяется на сервере, разбор открывается позже. */
class CourseAnswerApiTest extends IntegrationTest {

    private static final String COURSE = "informatics-ege";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private RequestPostProcessor newStudent() {
        return asStudent("ca-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private ResultActions answer(RequestPostProcessor student, String taskId, String answer) throws Exception {
        return mvc.perform(post("/api/courses/" + COURSE + "/tasks/" + taskId + "/answer").with(student).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("answer", answer))));
    }

    private void pause() throws InterruptedException {
        Thread.sleep(CourseCheckService.MIN_ANSWER_INTERVAL.toMillis() + 50);
    }

    @Test
    void correctAnswerSolvesTaskAndOpensSolution() throws Exception {
        RequestPostProcessor student = newStudent();
        answer(student, "inf01-1", " 24576 ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.taskSolved").value(true))
                .andExpect(jsonPath("$.solutionHtml").value(containsString("24576")));

        mvc.perform(get("/courses/" + COURSE + "/01-information").with(student))
                .andExpect(content().string(containsString("course-task task-solved")))
                .andExpect(content().string(containsString("Разбор решения")))
                .andExpect(content().string(containsString("data-last=\"24576\"")));
    }

    @Test
    void textAnswersIgnoreCaseAndSpaces() throws Exception {
        answer(newStudent(), "inf06-3", "б а в г а").andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void solutionOpensOnlyAfterThreeFails() throws Exception {
        RequestPostProcessor student = newStudent();
        answer(student, "inf01-3", "4")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.fails").value(1))
                .andExpect(jsonPath("$.failsToSolution").value(2))
                .andExpect(jsonPath("$.solutionHtml").value(nullValue()));
        pause();
        answer(student, "inf01-3", "6").andExpect(jsonPath("$.solutionHtml").value(nullValue()));
        mvc.perform(get("/courses/" + COURSE + "/01-information").with(student))
                .andExpect(content().string(containsString("Разбор откроется")));
        pause();
        answer(student, "inf01-3", "30")
                .andExpect(jsonPath("$.taskSolved").value(false))
                .andExpect(jsonPath("$.failsToSolution").value(0))
                .andExpect(jsonPath("$.solutionHtml").value(containsString("2⁵")));
    }

    @Test
    void truthTableReportsWrongRows() throws Exception {
        answer(newStudent(), "inf03-6", "11010111")
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.mismatches").value(1));
    }

    @Test
    void graphMatchingAndChoiceAreChecked() throws Exception {
        RequestPostProcessor student = newStudent();
        answer(student, "inf09-2", "4,2,6,1,3,5").andExpect(jsonPath("$.correct").value(true));
        pause();
        answer(student, "inf09-3", "1,2,3,4,5,6").andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.mismatches").isNumber());
        pause();
        answer(student, "inf01-5", "1").andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void lessonPageDoesNotLeakAnswersOrSolutions() throws Exception {
        mvc.perform(get("/courses/" + COURSE + "/03-logic-basics").with(newStudent()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-type=\"TRUTH_TABLE\"")))
                .andExpect(content().string(containsString("class=\"tt-cell\"")))
                .andExpect(content().string(containsString("data-widget=\"logic-calc\"")))
                .andExpect(content().string(not(containsString("11010101"))))
                .andExpect(content().string(not(containsString("Правильная таблица"))))
                .andExpect(content().string(not(containsString("codemirror"))));
    }

    @Test
    void codeEndpointsRejectAnswerTasksAndViceVersa() throws Exception {
        mvc.perform(get("/api/courses/" + COURSE + "/tasks/inf01-1/tests").with(newStudent()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/python-ege/tasks/py01-1/answer").with(newStudent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyOrHugeAnswersAndThrottles() throws Exception {
        answer(newStudent(), "inf01-1", "  ").andExpect(status().isBadRequest());
        answer(newStudent(), "inf01-1", "1".repeat(CourseAnswers.MAX_LENGTH + 1)).andExpect(status().isBadRequest());
        RequestPostProcessor student = newStudent();
        answer(student, "inf01-1", "1").andExpect(status().isOk());
        answer(student, "inf01-1", "2").andExpect(status().isTooManyRequests());
    }

    @Test
    void coursesListShowsInformaticsFirst() throws Exception {
        String html = mvc.perform(get("/courses").with(newStudent())).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.indexOf("Основы информатики для ЕГЭ"))
                .isPositive().isLessThan(html.indexOf("Python для ЕГЭ по информатике"));
    }
}
