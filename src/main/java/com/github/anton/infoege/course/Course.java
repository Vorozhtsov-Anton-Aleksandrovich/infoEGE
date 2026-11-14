package com.github.anton.infoege.course;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Курс из папки {@code courses/<slug>/}: контент лежит в git, а не в БД, и держится в памяти.
 * HTML уроков и условий уже отрисован и санитизирован при загрузке.
 */
public record Course(String slug, String title, String description, List<Lesson> lessons) {

    public Optional<Lesson> lesson(String lessonSlug) {
        return lessons.stream().filter(l -> l.slug().equals(lessonSlug)).findFirst();
    }

    public int taskCount() {
        return lessons.stream().mapToInt(l -> l.tasks().size()).sum();
    }

    public List<CourseTask> tasks() {
        return lessons.stream().flatMap(l -> l.tasks().stream()).toList();
    }

    public Optional<CourseTask> task(String taskId) {
        return tasks().stream().filter(t -> t.id().equals(taskId)).findFirst();
    }

    /** Урок курса. number — порядковый номер с 1. */
    public record Lesson(int number, String slug, String title, String html, List<CourseTask> tasks) {
    }

    /** Задача урока. Ожидаемый вывод скрытых тестов клиенту не отдаётся. */
    public record CourseTask(String id, String lessonSlug, String title, String statementHtml, String hintHtml,
                             String starterCode, List<CourseTest> tests) {

        public List<CourseTest> openTests() {
            return tests.stream().filter(t -> !t.hidden()).toList();
        }
    }

    /** Тест задачи: stdin, ожидаемый stdout и файлы, которые кладутся рядом с программой. */
    public record CourseTest(String input, String output, boolean hidden, Map<String, String> files) {

        /** Первые строки файлов — для блока «Примеры». */
        public Map<String, String> filePreviews(int maxLines) {
            Map<String, String> previews = new LinkedHashMap<>();
            files.forEach((name, content) -> {
                String[] lines = content.split("\n", -1);
                String head = String.join("\n", Arrays.copyOf(lines, Math.min(lines.length, maxLines)));
                previews.put(name, lines.length > maxLines ? head + "\n…" : head);
            });
            return previews;
        }
    }
}
