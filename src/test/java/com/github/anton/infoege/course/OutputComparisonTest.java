package com.github.anton.infoege.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparisonTest {

    @Test
    void ignoresTrailingSpacesAndTrailingEmptyLines() {
        assertThat(OutputComparison.matches("1 2  \n3\t\n\n\n", "1 2\n3")).isTrue();
    }

    @Test
    void treatsWindowsLineEndingsAsUnix() {
        assertThat(OutputComparison.matches("a\r\nb\r\n", "a\nb")).isTrue();
        assertThat(OutputComparison.normalize("a\rb")).isEqualTo("a\nb");
    }

    @Test
    void keepsLeadingSpacesAndInnerEmptyLines() {
        assertThat(OutputComparison.matches(" 5", "5")).isFalse();
        assertThat(OutputComparison.matches("a\n\nb", "a\nb")).isFalse();
        assertThat(OutputComparison.normalize("a\n\nb\n")).isEqualTo("a\n\nb");
    }

    @Test
    void comparesWholeLines() {
        assertThat(OutputComparison.matches("10", "1")).isFalse();
        assertThat(OutputComparison.matches("Hello", "hello")).isFalse();
        assertThat(OutputComparison.matches("3.0", "3")).isFalse();
    }

    @Test
    void emptyAndNullOutput() {
        assertThat(OutputComparison.normalize(null)).isEmpty();
        assertThat(OutputComparison.matches("\n\n", "")).isTrue();
    }
}
