package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.student.StudentDashboardService;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Дашборд на главной ученика: решено, правильность по номерам, активность; скрытые результаты не раскрываются. */
class StudentDashboardTest extends AttemptTestSupport {

    @Autowired
    StudentDashboardService dashboardService;

    @Test
    void countsSolvedTasksAndHidesTeacherOnlyCorrectness() throws Exception {
        User student = student();
        Task a = task(5, 1, 1, "1");
        Task b = task(5, 1, 1, "2");
        Task c = task(8, 1, 1, "3");
        Assignment open = assign(variant(235, a, c), student);
        Assignment hidden = assign(variant(null, b), student, ShowResultsMode.TEACHER_ONLY);

        Attempt first = attemptService.start(open.getId(), student.getId());
        attemptService.saveAnswer(first.getId(), student.getId(), a.getId(), List.of(List.of("1")));
        attemptService.saveAnswer(first.getId(), student.getId(), c.getId(), List.of(List.of("0")));
        attemptService.finish(first.getId(), student.getId());
        Attempt second = attemptService.start(hidden.getId(), student.getId());
        attemptService.saveAnswer(second.getId(), student.getId(), b.getId(), List.of(List.of("2")));
        attemptService.finish(second.getId(), student.getId());

        StudentDashboardService.Dashboard d = dashboardService.dashboard(student.getId());
        assertThat(d.answered()).isEqualTo(3);
        // Задание b решено верно, но результаты скрыты преподавателем: в правильность не входит.
        assertThat(d.checked()).isEqualTo(2);
        assertThat(d.correct()).isEqualTo(1);
        assertThat(d.byNumber()).containsExactly(
                new StudentDashboardService.NumberRow(5, 2, 1, 1),
                new StudentDashboardService.NumberRow(8, 1, 1, 0));
        assertThat(d.yearTotal()).isEqualTo(3);
        assertThat(d.activeDays()).isEqualTo(1);
        assertThat(d.weeks()).hasSize(53);
        assertThat(d.weeks().stream().flatMap(w -> w.days().stream()).filter(day -> day.count() == 3)).hasSize(1);

        String page = mvc.perform(get("/student").with(asStudent(student.getLogin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Решено заданий", "Активность", "По номерам ЕГЭ", "Варианты", "Задания",
                "hm-day hm-l2", open.getTaskSet().getTitle(), hidden.getTaskSet().getTitle());
        assertThat(page.indexOf(open.getTaskSet().getTitle())).as("вариант в разделе «Варианты»")
                .isLessThan(page.indexOf("<h2 class=\"spaced\">Задания</h2>"));
        assertThat(page.indexOf(hidden.getTaskSet().getTitle())).as("подборка в разделе «Задания»")
                .isGreaterThan(page.indexOf("<h2 class=\"spaced\">Задания</h2>"));
    }

    @Test
    void courseTasksCountInActivityOncePerDay() throws Exception {
        User student = student();
        java.time.Instant now = java.time.Instant.now();
        // Одна задача проверена трижды, другая — один раз (в другом курсе): за день — две задачи.
        courseProgress.saveAttempt(student.getId(), "python-ege", "py01-1", "x", 0, 1, false, now.minusSeconds(30));
        courseProgress.saveAttempt(student.getId(), "python-ege", "py01-1", "y", 0, 1, false, now.minusSeconds(20));
        courseProgress.saveAttempt(student.getId(), "python-ege", "py01-1", "z", 1, 1, true, now.minusSeconds(10));
        courseProgress.saveAttempt(student.getId(), "excel-ege", "xl01-2", "27", 1, 1, true, now);

        StudentDashboardService.Dashboard d = dashboardService.dashboard(student.getId());
        assertThat(d.answered()).as("«Решено заданий» — только работы").isZero();
        assertThat(d.yearTotal()).isEqualTo(2);
        assertThat(d.activeDays()).isEqualTo(1);
        StudentDashboardService.Day today = d.weeks().stream().flatMap(w -> w.days().stream())
                .filter(day -> day.count() > 0).findFirst().orElseThrow();
        assertThat(today.count()).isEqualTo(2);
        assertThat(today.courseCount()).isEqualTo(2);
        assertThat(today.title()).endsWith("2 задания (в курсах: 2)");

        String page = mvc.perform(get("/student").with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("hm-day hm-l1", "(в курсах: 2)");
    }

    @Autowired
    com.github.anton.infoege.course.CourseProgressRepository courseProgress;

    @Test
    void emptyDashboardRenders() throws Exception {
        User student = student();
        String page = mvc.perform(get("/student").with(asStudent(student.getLogin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("Пока нет решённых заданий.", "Вариантов пока нет.", "Подборок заданий пока нет.");
    }
}
