package com.github.anton.infoege.checking;

import com.github.anton.infoege.task.Scoring;

import java.util.List;

/**
 * Проверка ответа по ячейкам. Баллы берутся из задания (снимка), номера ЕГЭ здесь не используются.
 */
public final class AnswerChecker {

    private AnswerChecker() {
    }

    /**
     * @param correct true, если ответ полностью верный
     * @param points  начисленные баллы
     */
    public record Result(boolean correct, int points) {
    }

    /**
     * @param answer    ответ ученика или null, если ответа нет
     * @param expected  правильный ответ (rows × cols)
     * @param scoring   ALL_OR_NOTHING: всё или ничего; PER_ROW: доля верных строк от maxPoints, округление вниз
     * @param maxPoints баллы за полностью верный ответ
     */
    public static Result check(List<List<String>> answer, List<List<String>> expected, Scoring scoring, int maxPoints) {
        if (answer == null || answer.size() != expected.size()) {
            return new Result(false, 0);
        }
        int correctRows = 0;
        for (int r = 0; r < expected.size(); r++) {
            if (rowMatches(answer.get(r), expected.get(r))) {
                correctRows++;
            }
        }
        boolean allCorrect = correctRows == expected.size();
        int points = switch (scoring) {
            case ALL_OR_NOTHING -> allCorrect ? maxPoints : 0;
            case PER_ROW -> maxPoints * correctRows / expected.size();
        };
        return new Result(allCorrect, points);
    }

    private static boolean rowMatches(List<String> actual, List<String> expected) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (int c = 0; c < expected.size(); c++) {
            if (!AnswerNormalizer.normalizeCell(actual.get(c)).equals(AnswerNormalizer.normalizeCell(expected.get(c)))) {
                return false;
            }
        }
        return true;
    }
}
