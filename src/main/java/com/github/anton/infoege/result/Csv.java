package com.github.anton.infoege.result;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Простая запись CSV для Excel: разделитель «;», BOM для UTF-8, кавычки по необходимости.
 * Значения, которые Excel принял бы за формулу, начинаются с апострофа (защита от CSV-инъекций);
 * отрицательные числа не трогаются.
 */
final class Csv {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+([.,]\\d+)?");

    private final StringBuilder sb = new StringBuilder("﻿");

    Csv row(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(';');
            }
            sb.append(escape(values.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !NUMBER.matcher(s).matches()) {
            s = "'" + s;
        }
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
