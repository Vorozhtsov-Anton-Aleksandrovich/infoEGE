package com.github.anton.infoege.course;

import com.github.anton.infoege.course.Course.AnswerFormat;
import com.github.anton.infoege.course.Course.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAnswersTest {

    private static boolean ok(AnswerFormat format, String expected, String actual) {
        String normalized = CourseAnswers.normalize(TaskType.ANSWER, format, expected);
        return CourseAnswers.matches(TaskType.ANSWER, format, List.of(normalized), actual);
    }

    @Test
    void intIgnoresSpacesAndLeadingZeros() {
        assertThat(ok(AnswerFormat.INT, "1234", " 1 234 ")).isTrue();
        assertThat(ok(AnswerFormat.INT, "1234", "01234")).isTrue();
        assertThat(ok(AnswerFormat.INT, "-5", "-5")).isTrue();
        assertThat(ok(AnswerFormat.INT, "1234", "1234.0")).isFalse();
        assertThat(ok(AnswerFormat.INT, "1234", "abc")).isFalse();
    }

    @Test
    void numberAcceptsCommaAndHundredthsTolerance() {
        assertThat(ok(AnswerFormat.NUMBER, "12.34", "12,34")).isTrue();
        assertThat(ok(AnswerFormat.NUMBER, "12.34", "12.345")).isTrue();
        assertThat(ok(AnswerFormat.NUMBER, "12.34", "12.33")).isTrue();
        assertThat(ok(AnswerFormat.NUMBER, "12.34", "12.32")).isFalse();
        assertThat(ok(AnswerFormat.NUMBER, "5", "5,00")).isTrue();
        assertThat(ok(AnswerFormat.NUMBER, "5", "пять")).isFalse();
    }

    @Test
    void textIgnoresCaseAndOuterSpaces() {
        assertThat(ok(AnswerFormat.TEXT, "Заречный", "  заречный ")).isTrue();
        assertThat(ok(AnswerFormat.TEXT, "Северный", "СЕВЕРНЫЙ")).isTrue();
        assertThat(ok(AnswerFormat.TEXT, "Северный", "Северный район")).isFalse();
    }

    @Test
    void intPairNeedsTwoIntegersInOrder() {
        assertThat(ok(AnswerFormat.INT_PAIR, "1912 849", "1912 849")).isTrue();
        assertThat(ok(AnswerFormat.INT_PAIR, "1912 849", "  1912    849 ")).isTrue();
        assertThat(ok(AnswerFormat.INT_PAIR, "1912 849", "1912;849")).isTrue();
        assertThat(ok(AnswerFormat.INT_PAIR, "1912 849", "849 1912")).isFalse();
        assertThat(ok(AnswerFormat.INT_PAIR, "1912 849", "1912")).isFalse();
    }

    @Test
    void validFormatRejectsBrokenReferenceAnswers() {
        assertThat(CourseAnswers.validFormat(AnswerFormat.INT, "12a")).isFalse();
        assertThat(CourseAnswers.validFormat(AnswerFormat.INT_PAIR, "12")).isFalse();
        assertThat(CourseAnswers.validFormat(AnswerFormat.NUMBER, "1.5")).isTrue();
    }
}
