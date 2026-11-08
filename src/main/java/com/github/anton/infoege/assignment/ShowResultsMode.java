package com.github.anton.infoege.assignment;

/**
 * Когда ученик видит свои результаты, правильные ответы и разборы.
 * INSTANT: во время работы вместо «Сохранить ответ» кнопка «Проверить ответ», которая сразу говорит
 * «верно/неверно» (без правильного ответа); исправлять можно сколько угодно, после завершения — как AFTER_FINISH.
 */
public enum ShowResultsMode {
    AFTER_FINISH,
    AFTER_DEADLINE,
    TEACHER_ONLY,
    INSTANT;

    public boolean isInstant() {
        return this == INSTANT;
    }
}
