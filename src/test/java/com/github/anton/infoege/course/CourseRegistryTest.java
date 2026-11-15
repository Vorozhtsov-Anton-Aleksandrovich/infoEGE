package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseRegistryTest {

    private static CourseRegistry load(String location) {
        return new CourseRegistry(new MarkdownRenderer(), new ObjectMapper(), location);
    }

    @Test
    void loadsPythonCourse() {
        CourseRegistry registry = load("classpath*:courses/*/course.json");

        Course course = registry.course("python-ege").orElseThrow();
        assertThat(course.lessons()).hasSize(13);
        assertThat(course.taskCount()).isEqualTo(72);
        assertThat(course.lessons().get(0).number()).isEqualTo(1);
        assertThat(course.task("py10-1").orElseThrow().tests().get(0).files()).containsKey("17.txt");
    }

    @Test
    void loadsInformaticsCourseFirst() {
        CourseRegistry registry = load("classpath*:courses/*/course.json");

        assertThat(registry.courses()).extracting(Course::slug).startsWith("informatics-ege", "excel-ege", "python-ege");
        Course course = registry.course("informatics-ege").orElseThrow();
        assertThat(course.lessons()).hasSize(10);
        assertThat(course.taskCount()).isEqualTo(76);
        assertThat(course.tasks()).extracting(Course.CourseTask::type)
                .contains(Course.TaskType.ANSWER, Course.TaskType.CHOICE, Course.TaskType.TRUTH_TABLE, Course.TaskType.MATCH_GRAPH)
                .doesNotContain(Course.TaskType.CODE);
        assertThat(course.tasks()).allMatch(Course.CourseTask::hasSolution);
        assertThat(course.lessons()).noneMatch(Course.Lesson::hasCodeTasks);
    }

    @Test
    void informaticsLessonsUseBlocksAndWidgets() {
        Course course = load("classpath*:courses/*/course.json").course("informatics-ege").orElseThrow();

        String html = course.lesson("02-number-systems").orElseThrow().html();
        assertThat(html).contains("data-widget=\"base-converter\"", "box-try", "data-quiz", "box-cheat", "data-steps",
                "href=\"/courses/python-ege/11-ege-tricks#py11-1\"",
                "src=\"/course-assets/informatics-ege/images/division-column.svg\"");
    }

    @Test
    void answersAreNormalizedAndKeptOutOfHtml() {
        Course course = load("classpath*:courses/*/course.json").course("informatics-ege").orElseThrow();

        Course.CourseTask table = course.task("inf03-6").orElseThrow();
        assertThat(table.variables()).containsExactly("x", "y", "z");
        assertThat(table.answers()).containsExactly("11010101");
        assertThat(table.truthRows()).hasSize(8).first().isEqualTo(List.of(0, 0, 0));
        assertThat(course.task("inf06-3").orElseThrow().answers()).containsExactly("бавга");
        assertThat(course.task("inf09-2").orElseThrow().numberList()).containsExactly(1, 2, 3, 4, 5, 6);
        for (Course.CourseTask task : course.tasks()) {
            assertThat(task.statementHtml()).as(task.id()).doesNotContain("Ответ: **");
        }
    }

    @Test
    void solutionOpensAfterSolveOrThreeFails() {
        Course.CourseTask task = load("classpath*:courses/*/course.json").course("informatics-ege").orElseThrow()
                .task("inf01-1").orElseThrow();

        assertThat(task.solutionOpen(false, 2)).isFalse();
        assertThat(task.solutionOpen(false, 3)).isTrue();
        assertThat(task.solutionOpen(true, 0)).isTrue();
    }

    @Test
    void loadsExcelCourseWithFilesAndFormats() {
        CourseRegistry registry = load("classpath*:courses/*/course.json");

        assertThat(registry.courses()).extracting(Course::slug)
                .containsExactly("informatics-ege", "excel-ege", "python-ege");
        Course course = registry.course("excel-ege").orElseThrow();
        assertThat(course.lessons()).hasSize(10);
        assertThat(course.taskCount()).isEqualTo(67);
        Course.CourseTask robot = course.task("xl09-1").orElseThrow();
        assertThat(robot.answerFormat()).isEqualTo(Course.AnswerFormat.INT_PAIR);
        assertThat(robot.files()).containsExactly("xl09-1.xlsx");
        assertThat(robot.answerPlaceholder()).isEqualTo("два числа через пробел");
        assertThat(registry.file("excel-ege", "xl09-1.xlsx")).isPresent();
        assertThat(registry.file("excel-ege", "../course.json")).isEmpty();
        assertThat(registry.file("excel-ege", "xl09-1.exe")).isEmpty();
        assertThat(registry.file("python-ege", "xl09-1.xlsx")).isEmpty();

        String html = course.lesson("06-sort-filter").orElseThrow().html();
        assertThat(html).contains("box-warn", "box-cheat")
                .doesNotContain("todo-sort-dialog.png", "СКРИНШОТ");
    }

    @Test
    void brokenAnswerTasksStopStartup() {
        assertThatThrownBy(() -> load("classpath*:test-courses/broken-answers/course.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad-type: неизвестный type «ESSAY»")
                .hasMessageContaining("no-answer: нет правильного ответа")
                .hasMessageContaining("bad-choice: ответ CHOICE — номер варианта от 1 до 2")
                .hasMessageContaining("bad-table: ответ TRUTH_TABLE — 4 символов 0/1")
                .hasMessageContaining("bad-graph: ответ MATCH_GRAPH")
                .hasMessageContaining("no-solution: у задачи без кода нужен разбор")
                .hasMessageContaining("неизвестный виджет «nope»")
                .hasMessageContaining("bad-format: ответ «двенадцать» не подходит к формату INT")
                .hasMessageContaining("no-file: нет файла files/missing.xlsx")
                .hasMessageContaining("bad-file-name: файл «../secret.xlsx»")
                .hasMessageContaining("ссылка :::python на несуществующую задачу python-ege/01-first-program/missing");
    }

    @Test
    void rewritesLessonImagesToCourseAssets() {
        Course course = load("classpath*:courses/*/course.json").course("python-ege").orElseThrow();

        String html = course.lesson("12-install-idle").orElseThrow().html();
        assertThat(html).contains("src=\"/course-assets/python-ege/images/python-installer.svg\"", "<table>");
        assertThat(html).doesNotContain("src=\"images/");
    }

    @Test
    void servesOnlyExistingImagesWithValidNames() {
        CourseRegistry registry = load("classpath*:courses/*/course.json");

        assertThat(registry.image("python-ege", "range.svg")).isPresent();
        assertThat(registry.image("python-ege", "missing.svg")).isEmpty();
        assertThat(registry.image("python-ege", "..%2Fcourse.json")).isEmpty();
        assertThat(registry.image("python-ege", "Range.SVG")).isEmpty();
        assertThat(registry.image("nope", "range.svg")).isEmpty();
    }

    @Test
    void expectedOutputsAreAlreadyNormalized() {
        // Эталоны строит course-tools/build_tasks.py той же нормализацией, что и сервер.
        for (Course.CourseTask task : load("classpath*:courses/*/course.json").course("python-ege").orElseThrow().tasks()) {
            for (Course.CourseTest test : task.tests()) {
                assertThat(OutputComparison.normalize(test.output())).as(task.id()).isEqualTo(test.output());
            }
        }
    }

    @Test
    void referenceSolutionsAreNotOnClasspath() {
        assertThat(new ClassPathResource("course-tools/solutions/py01-1.py").exists()).isFalse();
        assertThat(new ClassPathResource("courses/python-ege/solutions/py01-1.py").exists()).isFalse();
        assertThat(new ClassPathResource("static/solutions/py01-1.py").exists()).isFalse();
    }

    @Test
    void brokenCourseStopsStartupWithAllProblems() {
        assertThatThrownBy(() -> load("classpath*:test-courses/broken/course.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нет файла lessons/missing.md")
                .hasMessageContaining("dup-1: id задачи уже используется")
                .hasMessageContaining("only-hidden: нужен хотя бы один открытый тест")
                .hasMessageContaining("нет файла картинки images/absent.svg")
                .hasMessageContaining("https://example.com/x.png");
    }
}
