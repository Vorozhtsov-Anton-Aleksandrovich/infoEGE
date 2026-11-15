package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.AnswerFormat;
import com.github.anton.infoege.course.Course.TaskType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Сравнение ответа ученика с эталоном для задач без кода. Эталоны нормализуются этим же методом при загрузке курса.
 */
final class CourseAnswers {

    public static final int MAX_LENGTH = 500;
    /** Точность сравнения для формата NUMBER. */
    static final BigDecimal NUMBER_TOLERANCE = new BigDecimal("0.01");

    private static final Pattern INT = Pattern.compile("[+-]?\\d+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");
    private static final Pattern INT_PAIR = Pattern.compile("[+-]?\\d+ [+-]?\\d+");

    private CourseAnswers() {
    }

    static String normalize(TaskType type, String raw) {
        return normalize(type, AnswerFormat.FREE, raw);
    }

    /**
     * ANSWER: по формату ответа (см. {@link AnswerFormat}). Остальные типы: только пробелы убираются —
     * формат ответа задаёт интерфейс. Неразборчивый ввод (например, буквы в INT) возвращается как есть
     * и просто не совпадёт с эталоном.
     */
    static String normalize(TaskType type, AnswerFormat format, String raw) {
        if (raw == null) {
            return "";
        }
        if (type != TaskType.ANSWER) {
            return raw.replaceAll("\\s+", "");
        }
        String trimmed = raw.strip();
        return switch (format) {
            case FREE -> trimmed.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).replace('ё', 'е').replace(',', '.');
            case TEXT -> trimmed.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).replace('ё', 'е');
            case INT -> {
                String s = trimmed.replaceAll("\\s+", "");
                yield INT.matcher(s).matches() ? new BigInteger(s).toString() : s;
            }
            case NUMBER -> {
                String s = trimmed.replaceAll("\\s+", "").replace(',', '.');
                yield NUMBER.matcher(s).matches() ? new BigDecimal(s).stripTrailingZeros().toPlainString() : s;
            }
            case INT_PAIR -> {
                String s = trimmed.replaceAll("[\\s;,]+", " ");
                if (!INT_PAIR.matcher(s).matches()) {
                    yield s;
                }
                String[] p = s.split(" ");
                yield new BigInteger(p[0]) + " " + new BigInteger(p[1]);
            }
        };
    }

    /** Правильный ли ответ: для NUMBER — с точностью {@link #NUMBER_TOLERANCE}, иначе точное совпадение. */
    static boolean matches(TaskType type, AnswerFormat format, List<String> expected, String raw) {
        String actual = normalize(type, format, raw);
        if (type == TaskType.ANSWER && format == AnswerFormat.NUMBER && NUMBER.matcher(actual).matches()) {
            BigDecimal a = new BigDecimal(actual);
            return expected.stream().anyMatch(e -> new BigDecimal(e).subtract(a).abs().compareTo(NUMBER_TOLERANCE) <= 0);
        }
        return expected.contains(actual);
    }

    /** Подходит ли эталон к формату (проверяется при загрузке курса). */
    static boolean validFormat(AnswerFormat format, String normalized) {
        return switch (format) {
            case INT -> INT.matcher(normalized).matches();
            case NUMBER -> NUMBER.matcher(normalized).matches();
            case INT_PAIR -> INT_PAIR.matcher(normalized).matches();
            default -> !normalized.isEmpty();
        };
    }

    /** Сколько позиций не совпало: для таблицы истинности — строк, для графа — вершин. -1, если формат другой. */
    static int mismatches(TaskType type, String actual, String expected) {
        if (type == TaskType.TRUTH_TABLE) {
            if (actual.length() != expected.length()) {
                return -1;
            }
            int wrong = 0;
            for (int i = 0; i < expected.length(); i++) {
                if (actual.charAt(i) != expected.charAt(i)) {
                    wrong++;
                }
            }
            return wrong;
        }
        if (type == TaskType.MATCH_GRAPH) {
            String[] a = actual.split(",", -1);
            String[] e = expected.split(",", -1);
            if (a.length != e.length) {
                return -1;
            }
            int wrong = 0;
            for (int i = 0; i < e.length; i++) {
                if (!a[i].equals(e[i])) {
                    wrong++;
                }
            }
            return wrong;
        }
        return -1;
    }
}
