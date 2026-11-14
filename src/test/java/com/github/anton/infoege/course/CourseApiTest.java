package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.course.Course.CourseTask;
import com.github.anton.infoege.course.Course.CourseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.List;
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

class CourseApiTest extends IntegrationTest {

    private static final String TASK = "py05-5";   // «Найди ошибку»: 1 открытый тест и 3 скрытых
    private static final String BASE = "/api/courses/python-ege/tasks/" + TASK;

    @Autowired
    MockMvc mvc;

    @Autowired
    CourseRegistry registry;

    @Autowired
    ObjectMapper json;

    @Value("${app.courses.pyodide-version}")
    String pyodideVersion;

    @Value("${app.courses.codemirror-version}")
    String codemirrorVersion;

    /** Отдельный ученик на каждый тест: лимит «раз в 2 секунды» считается на пользователя. */
    private RequestPostProcessor newStudent() {
        return asStudent("cs-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private CourseTask task() {
        return registry.course("python-ege").orElseThrow().task(TASK).orElseThrow();
    }

    private List<String> correctOutputs() {
        return task().tests().stream().map(CourseTest::output).toList();
    }

    private List<String> statuses(String status) {
        return Collections.nCopies(task().tests().size(), status);
    }

    private ResultActions check(RequestPostProcessor student, String code, List<String> outputs, List<String> statuses)
            throws Exception {
        String body = json.writeValueAsString(Map.of("code", code, "outputs", outputs, "statuses", statuses));
        return mvc.perform(post(BASE + "/check").with(student).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void testsEndpointHidesExpectedOutputOfHiddenTests() throws Exception {
        mvc.perform(get(BASE + "/tests").with(newStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].hidden").value(false))
                .andExpect(jsonPath("$[0].output").value("10"))
                .andExpect(jsonPath("$[1].hidden").value(true))
                .andExpect(jsonPath("$[1].input").exists())
                .andExpect(jsonPath("$[1].output").doesNotExist())
                .andExpect(content().string(not(containsString("76205685"))));
    }

    @Test
    void testsEndpointSendsTaskFiles() throws Exception {
        mvc.perform(get("/api/courses/python-ege/tasks/py10-1/tests").with(newStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].files['17.txt']").exists());
    }

    @Test
    void correctOutputsSolveTaskAndRestoreCodeOnLessonPage() throws Exception {
        RequestPostProcessor student = newStudent();
        check(student, "print('мой код')", correctOutputs(), statuses("ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solved").value(true))
                .andExpect(jsonPath("$.taskSolved").value(true))
                .andExpect(jsonPath("$.passed").value(4))
                .andExpect(jsonPath("$.results[1].hidden").value(true))
                .andExpect(jsonPath("$.results[1].status").value("passed"));

        mvc.perform(get("/courses/python-ege/05-for-loop").with(student))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("решена ✅")))
                .andExpect(content().string(containsString("print(&#39;мой код&#39;)")));
    }

    @Test
    void outputsAreComparedAfterNormalization() throws Exception {
        List<String> outputs = correctOutputs().stream().map(o -> o + "  \r\n\n").toList();
        check(newStudent(), "x", outputs, statuses("ok")).andExpect(jsonPath("$.solved").value(true));
    }

    @Test
    void wrongOutputsDoNotSolveTask() throws Exception {
        List<String> outputs = List.of("10", "1", "4950", "76193340");   // ответы стартового кода с ошибкой
        check(newStudent(), "x", outputs, statuses("ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solved").value(false))
                .andExpect(jsonPath("$.taskSolved").value(false))
                .andExpect(jsonPath("$.passed").value(2))
                .andExpect(jsonPath("$.results[2].status").value("wrong"));
    }

    @Test
    void errorAndTimeoutStatusesFailEvenWithRightOutput() throws Exception {
        check(newStudent(), "x", correctOutputs(), List.of("ok", "error", "timeout", "ok"))
                .andExpect(jsonPath("$.solved").value(false))
                .andExpect(jsonPath("$.results[1].status").value("error"))
                .andExpect(jsonPath("$.results[2].status").value("timeout"));
    }

    @Test
    void solvedTaskStaysSolvedAfterWrongCheck() throws Exception {
        RequestPostProcessor student = newStudent();
        check(student, "good", correctOutputs(), statuses("ok")).andExpect(jsonPath("$.taskSolved").value(true));
        Thread.sleep(CourseCheckService.MIN_INTERVAL.toMillis() + 100);
        check(student, "bad", List.of("0", "0", "0", "0"), statuses("ok"))
                .andExpect(jsonPath("$.solved").value(false))
                .andExpect(jsonPath("$.taskSolved").value(true));
    }

    @Test
    void checksAreRateLimitedPerUser() throws Exception {
        RequestPostProcessor student = newStudent();
        check(student, "x", correctOutputs(), statuses("ok")).andExpect(status().isOk());
        check(student, "x", correctOutputs(), statuses("ok")).andExpect(status().isTooManyRequests());
        check(newStudent(), "x", correctOutputs(), statuses("ok")).andExpect(status().isOk());
    }

    @Test
    void rejectsMalformedRequests() throws Exception {
        check(newStudent(), "x", List.of("10"), List.of("ok")).andExpect(status().isBadRequest());
        check(newStudent(), "x", correctOutputs(), statuses("hacked")).andExpect(status().isBadRequest());
        check(newStudent(), "x".repeat(CourseCheckService.MAX_CODE_LENGTH + 1), correctOutputs(), statuses("ok"))
                .andExpect(status().isBadRequest());
        List<String> huge = List.of("x".repeat(CourseCheckService.MAX_OUTPUT_LENGTH + 1), "", "", "");
        check(newStudent(), "x", huge, statuses("ok")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownTaskOrCourseIs404() throws Exception {
        mvc.perform(get("/api/courses/python-ege/tasks/nope/tests").with(newStudent())).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/nope/tasks/" + TASK + "/tests").with(newStudent())).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/python-ege/tasks/nope/check").with(newStudent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsRedirectedAndCsrfIsRequired() throws Exception {
        mvc.perform(get(BASE + "/tests")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post(BASE + "/check").with(newStudent()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void workerGetsItsOwnContentSecurityPolicy() throws Exception {
        mvc.perform(get("/js/py-worker.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("'wasm-unsafe-eval'")));
        mvc.perform(get("/courses/python-ege/01-first-program").with(newStudent()))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-eval"))))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void servesPyodideAndCodeMirrorWithoutCdn() throws Exception {
        mvc.perform(get("/pyodide/" + pyodideVersion + "/pyodide.asm.wasm"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/wasm"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
        mvc.perform(get("/pyodide/" + pyodideVersion + "/pyodide.mjs"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")));
        mvc.perform(get("/webjars/codemirror/" + codemirrorVersion + "/lib/codemirror.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/codemirror/" + codemirrorVersion + "/mode/python/python.js")).andExpect(status().isOk());
    }
}
