package com.github.anton.infoege.course;

import com.github.anton.infoege.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseWebTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/courses")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/course-assets/python-ege/images/range.svg")).andExpect(status().is3xxRedirection());
    }

    @Test
    void studentAndTeacherSeeCoursesTab() throws Exception {
        mvc.perform(get("/courses").with(asStudent("course-student")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Python для ЕГЭ по информатике")))
                .andExpect(content().string(containsString("href=\"/courses\"")));
        mvc.perform(get("/courses").with(asTeacher())).andExpect(status().isOk());
    }

    @Test
    void studentHomeShowsCourse() throws Exception {
        mvc.perform(get("/student").with(asStudent("course-student")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/courses/python-ege\"")));
    }

    @Test
    void courseAndLessonPages() throws Exception {
        mvc.perform(get("/courses/python-ege").with(asStudent("course-student")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/courses/python-ege/13-pycharm")));

        mvc.perform(get("/courses/python-ege/05-for-loop").with(asStudent("course-student")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/course-assets/python-ege/images/range.svg")))
                .andExpect(content().string(containsString("<details>")))
                .andExpect(content().string(containsString("/courses/python-ege/04-conditions")))
                .andExpect(content().string(containsString("/courses/python-ege/06-while-loop")))
                // Скрытый тест py05-5 (n = 12345) на странице не показывается.
                .andExpect(content().string(not(containsString("76205685"))));
    }

    @Test
    void inputFieldOnlyForTasksThatReadInput() throws Exception {
        // В уроке 1 программы ничего не читают — поля «Ввод» нет; в уроке 2 оно есть.
        mvc.perform(get("/courses/python-ege/01-first-program").with(asStudent("course-student")))
                .andExpect(content().string(not(containsString("class=\"run-input"))))
                .andExpect(content().string(containsString("<details class=\"course-task task-new\"")));
        mvc.perform(get("/courses/python-ege/02-variables-input").with(asStudent("course-student")))
                .andExpect(content().string(containsString("class=\"run-input")));
    }

    @Test
    void unknownCourseOrLessonIs404() throws Exception {
        mvc.perform(get("/courses/nope").with(asStudent("course-student"))).andExpect(status().isNotFound());
        mvc.perform(get("/courses/python-ege/nope").with(asStudent("course-student"))).andExpect(status().isNotFound());
    }

    @Test
    void servesCourseImages() throws Exception {
        mvc.perform(get("/course-assets/python-ege/images/range.svg").with(asStudent("course-student")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/svg+xml")))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")));
    }

    @Test
    void rejectsBadImageNames() throws Exception {
        mvc.perform(get("/course-assets/python-ege/images/missing.svg").with(asStudent("course-student")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/course-assets/python-ege/images/course.json").with(asStudent("course-student")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/course-assets/python-ege/images/..%2F..%2Fcourse.json").with(asStudent("course-student")))
                .andExpect(status().is4xxClientError());
    }
}
