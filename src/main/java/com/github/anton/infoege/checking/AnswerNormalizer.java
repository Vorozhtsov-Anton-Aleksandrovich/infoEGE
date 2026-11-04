package com.github.anton.infoege.checking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Ответ хранится как таблица строк: строки × столбцы.
 * Текстовая запись: одна строка таблицы на строку текста, ячейки разделены пробелом, табуляцией или «;».
 */
public final class AnswerNormalizer {

    private static final Pattern CELL_SEPARATOR = Pattern.compile("[\\s;]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private AnswerNormalizer() {
    }

    /** Разбирает текст в таблицу, пустые строки пропускаются. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            rows.add(List.of(CELL_SEPARATOR.split(trimmed)));
        }
        return rows;
    }

    /** Обратное к {@link #parse}: для показа ответа в форме редактирования. */
    public static String format(List<List<String>> table) {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : table) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(String.join(" ", row));
        }
        return sb.toString();
    }

    /**
     * Ячейка для сравнения: без пробелов по краям, внутренние пробелы схлопнуты,
     * десятичный разделитель единый (запятая = точка), регистр не важен.
     */
    public static String normalizeCell(String cell) {
        if (cell == null) {
            return "";
        }
        return SPACES.matcher(cell.trim()).replaceAll(" ").replace(',', '.').toUpperCase(Locale.ROOT);
    }

    /** Проверяет, что таблица ровно rows × cols. */
    public static boolean hasShape(List<List<String>> table, int rows, int cols) {
        if (table.size() != rows) {
            return false;
        }
        return table.stream().allMatch(row -> row.size() == cols);
    }
}
