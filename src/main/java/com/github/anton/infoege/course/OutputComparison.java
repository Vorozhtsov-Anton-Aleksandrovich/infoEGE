package com.github.anton.infoege.course;

/**
 * Сравнение вывода программы с ожидаемым. Та же нормализация, что в {@code course-tools/validate_tasks.py}
 * ({@code '\n'.join(l.rstrip() for l in out.rstrip().splitlines())}) — держать их одинаковыми.
 */
public final class OutputComparison {

    private OutputComparison() {
    }

    /** Переводы строк к \n, пробелы в конце каждой строки и пустые строки в конце вывода убираются. */
    public static String normalize(String output) {
        if (output == null) {
            return "";
        }
        String text = output.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
        StringBuilder result = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(line.stripTrailing());
        }
        return result.toString();
    }

    public static boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }
}
