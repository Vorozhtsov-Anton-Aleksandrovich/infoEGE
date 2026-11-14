package com.github.anton.infoege.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.anton.infoege.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Test;

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
