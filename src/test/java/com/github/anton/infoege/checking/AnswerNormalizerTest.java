package com.github.anton.infoege.checking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerNormalizerTest {

    @Test
    void parsesSingleValue() {
        assertThat(AnswerNormalizer.parse("  42 ")).isEqualTo(List.of(List.of("42")));
    }

    @Test
    void parsesTableWithDifferentSeparators() {
        assertThat(AnswerNormalizer.parse("10 20\r\n30\t40\n\n50;  60\n"))
                .isEqualTo(List.of(List.of("10", "20"), List.of("30", "40"), List.of("50", "60")));
    }

    @Test
    void formatIsInverseOfParse() {
        var table = List.of(List.of("1", "2"), List.of("3", "4"));

        assertThat(AnswerNormalizer.parse(AnswerNormalizer.format(table))).isEqualTo(table);
    }

    @Test
    void checksShape() {
        var table = List.of(List.of("1", "2"), List.of("3"));

        assertThat(AnswerNormalizer.hasShape(table, 2, 2)).isFalse();
        assertThat(AnswerNormalizer.hasShape(List.of(List.of("1", "2")), 1, 2)).isTrue();
    }
}
