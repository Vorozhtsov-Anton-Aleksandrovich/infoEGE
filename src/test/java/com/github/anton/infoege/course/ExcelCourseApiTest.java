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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Курс «Excel для ЕГЭ»: файлы задач, форматы ответов, разбор только после решения или 3 ошибок. */
class ExcelCourseApiTest extends IntegrationTest {

    private static final String COURSE = "excel-ege";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    CourseRegistry registry;

    private RequestPostProcessor newStudent() {
        return asStudent("xl-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private String answerOf(String taskId) {
        return registry.course(COURSE).orElseThrow().task(taskId).orElseThrow().answers().get(0);
    }

    private ResultActions answer(RequestPostProcessor student, String taskId, String answer) throws Exception {
        return mvc.perform(post("/api/courses/" + COURSE + "/tasks/" + taskId + "/answer").with(student).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("answer", answer))));
    }

    private void pause() throws InterruptedException {
        Thread.sleep(CourseCheckService.MIN_ANSWER_INTERVAL.toMillis() + 50);
    }

    @Test
    void taskFileIsDownloadedAsAttachment() throws Exception {
        mvc.perform(get("/course-assets/" + COURSE + "/files/xl09-1.xlsx").with(newStudent()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("xl09-1.xlsx")));
    }

    @Test
    void foreignOrStrangeFilesAre404() throws Exception {
        RequestPostProcessor s = newStudent();
        mvc.perform(get("/course-assets/" + COURSE + "/files/nope.xlsx").with(s)).andExpect(status().isNotFound());
        mvc.perform(get("/course-assets/python-ege/files/xl09-1.xlsx").with(s)).andExpect(status().isNotFound());
        mvc.perform(get("/course-assets/" + COURSE + "/files/course.json").with(s)).andExpect(status().isNotFound());
        // закодированный «/» в пути отбивает ещё файрвол Spring Security (400) — до контроллера запрос не доходит
        mvc.perform(get("/course-assets/" + COURSE + "/files/..%2Fcourse.json").with(s)).andExpect(status().is4xxClientError());
        mvc.perform(get("/course-assets/" + COURSE + "/files/XL09-1.XLSX").with(s)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousCannotDownload() throws Exception {
        mvc.perform(get("/course-assets/" + COURSE + "/files/xl09-1.xlsx"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void lessonShowsDownloadButtonButNoAnswerOrSolution() throws Exception {
        String answer = answerOf("xl09-6");
        mvc.perform(get("/courses/" + COURSE + "/09-task18").with(newStudent()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/course-assets/excel-ege/files/xl09-6.xlsx\"")))
                .andExpect(content().string(containsString("placeholder=\"два числа через пробел\"")))
                .andExpect(content().string(not(containsString(answer))))
                .andExpect(content().string(not(containsString("Строим рядом с полем таблицу"))));
    }

    @Test
    void solutionEndpointOpensAfterSolve() throws Exception {
        RequestPostProcessor student = newStudent();
        String url = "/api/courses/" + COURSE + "/tasks/xl09-1/solution";
        mvc.perform(get(url).with(student)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.html").doesNotExist());
        answer(student, "xl09-1", answerOf("xl09-1").replace(" ", "   "))
                .andExpect(jsonPath("$.correct").value(true));
        mvc.perform(get(url).with(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.html").value(containsString("МАКС")));
    }

    @Test
    void solutionEndpointOpensAfterThreeFails() throws Exception {
        RequestPostProcessor student = newStudent();
        String url = "/api/courses/" + COURSE + "/tasks/xl04-1/solution";
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(url).with(student)).andExpect(status().isForbidden());
            answer(student, "xl04-1", "0").andExpect(jsonPath("$.correct").value(false));
            pause();
        }
        mvc.perform(get(url).with(student)).andExpect(status().isOk());
    }

    @Test
    void numberAnswerAcceptsCommaAndHundredths() throws Exception {
        String expected = answerOf("xl04-1");   // средний балл, NUMBER
        answer(newStudent(), "xl04-1", expected.replace('.', ',')).andExpect(jsonPath("$.correct").value(true));
    }

    @Test
    void textAndChoiceAnswers() throws Exception {
        RequestPostProcessor student = newStudent();
        answer(student, "xl08-5", "  " + answerOf("xl08-5").toUpperCase() + " ").andExpect(jsonPath("$.correct").value(true));
        pause();
        answer(student, "xl02-2", "1").andExpect(jsonPath("$.correct").value(true));
    }
}
