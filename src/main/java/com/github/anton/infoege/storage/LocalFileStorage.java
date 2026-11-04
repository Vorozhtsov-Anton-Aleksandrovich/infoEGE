package com.github.anton.infoege.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Файлы на локальном диске. Имя файла: случайный UUID без расширения,
 * чтобы исходное имя от пользователя никогда не попадало в путь.
 */
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Path baseDir;

    public LocalFileStorage(Path baseDir) throws IOException {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        Files.createDirectories(this.baseDir);
    }

    @Override
    public String store(InputStream content) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        Path tmp = baseDir.resolve(key + ".tmp");
        try {
            Files.copy(content, tmp);
            Files.move(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return baseDir.resolve(key);
    }
}
