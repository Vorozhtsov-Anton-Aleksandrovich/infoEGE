package com.github.anton.infoege.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Хранилище содержимого файлов. Метаданные (имя, размер, тип) хранятся в БД отдельно.
 */
public interface FileStorage {

    /** Сохраняет содержимое под новым случайным ключом и возвращает этот ключ. */
    String store(InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
