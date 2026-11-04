package com.github.anton.infoege.task;

public enum FileKind {
    /** Файл для скачивания учеником. */
    ATTACHMENT,
    /** Картинка, которая вставляется в условие через {@code ![](file:имя.png)}. */
    IMAGE
}
