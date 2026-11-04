package com.github.anton.infoege.checking;

import com.github.anton.infoege.task.Scoring;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerCheckerTest {

    private static final List<List<String>> PAIR = List.of(List.of("10", "20"));
    private static final List<List<String>> TWO_ROWS = List.of(List.of("1", "2"), List.of("3", "4"));

    @Test
    void allOrNothing() {
        assertThat(AnswerChecker.check(List.of(List.of("10", "20")), PAIR, Scoring.ALL_OR_NOTHING, 2))
                .isEqualTo(new AnswerChecker.Result(true, 2));
        assertThat(AnswerChecker.check(List.of(List.of("10", "21")), PAIR, Scoring.ALL_OR_NOTHING, 2))
                .isEqualTo(new AnswerChecker.Result(false, 0));
    }

    @Test
    void perRowGivesPartialPoints() {
        var oneRowRight = List.of(List.of("1", "2"), List.of("3", "5"));

        assertThat(AnswerChecker.check(oneRowRight, TWO_ROWS, Scoring.PER_ROW, 2)).isEqualTo(new AnswerChecker.Result(false, 1));
        assertThat(AnswerChecker.check(TWO_ROWS, TWO_ROWS, Scoring.PER_ROW, 2)).isEqualTo(new AnswerChecker.Result(true, 2));
        assertThat(AnswerChecker.check(oneRowRight, TWO_ROWS, Scoring.ALL_OR_NOTHING, 2)).isEqualTo(new AnswerChecker.Result(false, 0));
    }

    @Test
    void perRowRoundsDown() {
        var expected = List.of(List.of("1"), List.of("2"), List.of("3"));

        assertThat(AnswerChecker.check(List.of(List.of("1"), List.of("x"), List.of("y")), expected, Scoring.PER_ROW, 2).points())
                .isZero();
        assertThat(AnswerChecker.check(List.of(List.of("1"), List.of("2"), List.of("y")), expected, Scoring.PER_ROW, 2).points())
                .isEqualTo(1);
    }

    @Test
    void comparesNormalizedCells() {
        var expected = List.of(List.of("2.5", "abc", "Hello World"));

        assertThat(AnswerChecker.check(List.of(List.of(" 2,5 ", "ABC", "hello   world")), expected, Scoring.ALL_OR_NOTHING, 1).correct())
                .isTrue();
    }

    @Test
    void missingOrMalformedAnswerGivesZero() {
        assertThat(AnswerChecker.check(null, PAIR, Scoring.ALL_OR_NOTHING, 1)).isEqualTo(new AnswerChecker.Result(false, 0));
        assertThat(AnswerChecker.check(List.of(List.of("10")), PAIR, Scoring.ALL_OR_NOTHING, 1).correct()).isFalse();
        assertThat(AnswerChecker.check(List.of(List.of("10", "")), PAIR, Scoring.PER_ROW, 1).points()).isZero();
    }

    @Test
    void zeroPointTaskIsStillMarkedCorrect() {
        assertThat(AnswerChecker.check(PAIR, PAIR, Scoring.ALL_OR_NOTHING, 0)).isEqualTo(new AnswerChecker.Result(true, 0));
    }
}
