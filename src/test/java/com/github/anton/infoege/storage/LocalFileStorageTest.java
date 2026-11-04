package com.github.anton.infoege.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesUnderRandomNameAndReadsBack() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir);

        String key = storage.store(new ByteArrayInputStream("данные".getBytes(StandardCharsets.UTF_8)));
        String other = storage.store(new ByteArrayInputStream("x".getBytes()));

        assertThat(key).isNotEqualTo(other).matches("[0-9a-f-]{36}");
        assertThat(Files.exists(dir.resolve(key))).isTrue();
        try (InputStream in = storage.open(key)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("данные");
        }
    }

    @Test
    void deletesFile() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir);
        String key = storage.store(new ByteArrayInputStream("x".getBytes()));

        storage.delete(key);

        assertThat(Files.exists(dir.resolve(key))).isFalse();
    }

    @Test
    void rejectsKeysThatAreNotUuids() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir);

        assertThatThrownBy(() -> storage.open("../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("..\\x")).isInstanceOf(IllegalArgumentException.class);
    }
}
