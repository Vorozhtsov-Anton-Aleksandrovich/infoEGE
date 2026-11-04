package com.github.anton.infoege.result;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void escapesFormulasButKeepsNegativeNumbers() {
        assertThat(Csv.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(Csv.escape("+1+2")).isEqualTo("'+1+2");
        assertThat(Csv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.escape("-cmd")).isEqualTo("'-cmd");
        assertThat(Csv.escape("-5")).isEqualTo("-5");
        assertThat(Csv.escape("-2,5")).isEqualTo("-2,5");
    }

    @Test
    void quotesSeparatorsAndNewlines() {
        assertThat(Csv.escape("a;b")).isEqualTo("\"a;b\"");
        assertThat(Csv.escape("line\nbreak")).isEqualTo("\"line\nbreak\"");
        assertThat(Csv.escape(null)).isEmpty();
        assertThat(Csv.escape(42)).isEqualTo("42");
    }

    @Test
    void startsWithBomAndUsesSemicolons() {
        String csv = new Csv().row(Arrays.asList("Ученик", null, 3)).toString();

        assertThat(csv).isEqualTo("﻿Ученик;;3\r\n");
    }
}
