package com.github.anton.infoege.taskset;

public enum TaskSetType {
    /** Подборка: порядок задаёт преподаватель, лимит времени необязателен. */
    COLLECTION,
    /** Вариант: задания по возрастанию номера ЕГЭ, лимит времени обязателен, как на экзамене. */
    VARIANT
}
