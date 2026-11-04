package com.github.anton.infoege.task;

/**
 * Стратегия проверки ответа.
 */
public enum Scoring {
    /** Полный балл только при полностью верной таблице, иначе 0. */
    ALL_OR_NOTHING,
    /** Балл пропорционально числу верных строк (округление вниз). */
    PER_ROW
}
