package com.github.anton.infoege.course;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Курс из папки {@code courses/<slug>/}: контент лежит в git, а не в БД, и держится в памяти.
 * HTML уроков и условий уже отрисован и санитизирован при загрузке.
 */
public record Course(String slug, String title, String description, int order, List<Lesson> lessons) {

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

        /** Нужны ли на странице редактор кода и Python в браузере. */
        public boolean hasCodeTasks() {
            return tasks.stream().anyMatch(CourseTask::isCode);
        }
    }

    /**
     * Тип задачи. CODE — программа на Python с тестами; остальные проверяются сравнением ответа на сервере,
     * эталон в браузер не отправляется.
     */
    public enum TaskType {
        /** Программа на Python, выводы по тестам сверяет сервер. */
        CODE,
        /** Короткий ответ: число или строка. */
        ANSWER,
        /** Один вариант из нескольких. Ответ — номер варианта с 1. */
        CHOICE,
        /** Столбец F таблицы истинности: строка из 0 и 1 длиной 2^n, наборы по порядку двоичного счёта. */
        TRUTH_TABLE,
        /** Сопоставление вершин графа с номерами пунктов таблицы. Ответ — номера через запятую в порядке вершин. */
        MATCH_GRAPH
    }

    /**
     * Как сравнивать ответ задачи ANSWER. FREE — по умолчанию: без пробелов, регистра, «ё», запятая = точка.
     * INT — целое (пробелы внутри игнорируются), NUMBER — число с точностью 0.01, TEXT — строка без учёта
     * регистра и крайних пробелов, INT_PAIR — два целых через пробел (как в заданиях 18 и 26).
     */
    public enum AnswerFormat {
        FREE, INT, NUMBER, TEXT, INT_PAIR
    }

    /**
     * Задача урока. Ожидаемый вывод скрытых тестов и правильные ответы клиенту не отдаются.
     *
     * @param answers      правильные ответы (для всех типов, кроме CODE); любой из них засчитывается
     * @param optionsHtml  варианты ответа CHOICE (HTML)
     * @param variables    переменные таблицы истинности или вершины графа (MATCH_GRAPH)
     * @param expression   формула над таблицей истинности (только для показа)
     * @param numbers      сколько пунктов в таблице MATCH_GRAPH
     * @param solutionHtml разбор: показывается после решения или после {@link #SOLUTION_AFTER_FAILS} неудачных попыток
     * @param answerFormat как сравнивать ответ ANSWER
     * @param files        файлы задачи из папки files/ курса (ученик их скачивает)
     */
    public record CourseTask(String id, String lessonSlug, TaskType type, String title, String statementHtml,
                             String hintHtml, String starterCode, List<CourseTest> tests, List<String> answers,
                             List<String> optionsHtml, List<String> variables, String expression, int numbers,
                             String solutionHtml, AnswerFormat answerFormat, List<String> files) {

        public String answerPlaceholder() {
            return switch (answerFormat) {
                case INT -> "целое число";
                case NUMBER -> "число, например 12,5";
                case INT_PAIR -> "два числа через пробел";
                default -> "";
            };
        }

        public static final int SOLUTION_AFTER_FAILS = 3;

        public boolean isCode() {
            return type == TaskType.CODE;
        }

        public String typeName() {
            return type.name();
        }

        public boolean hasSolution() {
            return solutionHtml != null && !solutionHtml.isEmpty();
        }

        /** Разбор открыт, если задача решена или ученик ошибся достаточно раз. */
        public boolean solutionOpen(boolean solved, int fails) {
            return hasSolution() && (solved || fails >= SOLUTION_AFTER_FAILS);
        }

        public List<CourseTest> openTests() {
            return tests.stream().filter(t -> !t.hidden()).toList();
        }

        public boolean hasFiles() {
            return !openTests().isEmpty() && !openTests().get(0).files().isEmpty();
        }

        /** Читает ли программа ввод: хотя бы в одном тесте есть stdin. Иначе поле «Ввод» не показывается. */
        public boolean needsInput() {
            return tests.stream().anyMatch(t -> !t.input().isEmpty());
        }

        /** Наборы значений таблицы истинности по порядку: 00…0, 00…1, …, 11…1. */
        public List<List<Integer>> truthRows() {
            int n = variables.size();
            List<List<Integer>> rows = new ArrayList<>(1 << n);
            for (int mask = 0; mask < (1 << n); mask++) {
                List<Integer> row = new ArrayList<>(n);
                for (int bit = n - 1; bit >= 0; bit--) {
                    row.add((mask >> bit) & 1);
                }
                rows.add(row);
            }
            return rows;
        }

        /** 1..numbers — варианты выбора в MATCH_GRAPH. */
        public List<Integer> numberList() {
            List<Integer> list = new ArrayList<>(numbers);
            for (int i = 1; i <= numbers; i++) {
                list.add(i);
            }
            return list;
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
