package com.github.anton.infoege.storage;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator(new StorageProperties(
            Path.of("unused"), DataSize.ofKilobytes(100), Set.of("txt", "xlsx", "png"), Set.of("png", "jpg")));

    @Test
    void acceptsWhitelistedFiles() {
        assertThat(validator.validate("27_A.txt", 10, false)).isNull();
        assertThat(validator.validate("Data.XLSX", 10, false)).isNull();
        assertThat(validator.validate("graph.jpg", 10, true)).isNull();
    }

    @Test
    void rejectsOtherExtensions() {
        assertThat(validator.validate("virus.exe", 10, false)).contains("Недопустимый тип");
        assertThat(validator.validate("noext", 10, false)).contains("Недопустимый тип");
        assertThat(validator.validate("page.html", 10, false)).contains("Недопустимый тип");
        assertThat(validator.validate("data.txt", 10, true)).contains("Недопустимый тип");
    }

    @Test
    void rejectsTooLargeAndEmptyFiles() {
        assertThat(validator.validate("big.txt", 101 * 1024, false)).contains("больше");
        assertThat(validator.validate("empty.txt", 0, false)).contains("пустой");
    }

    @Test
    void contentTypeComesFromExtension() {
        assertThat(validator.contentType("a.png")).isEqualTo("image/png");
        assertThat(validator.contentType("a.unknown")).isEqualTo("application/octet-stream");
    }

    @Test
    void cleanNameStripsPath() {
        assertThat(FileValidator.cleanName("C:\\Users\\me\\27.txt")).isEqualTo("27.txt");
        assertThat(FileValidator.cleanName("../../etc/passwd")).isEqualTo("passwd");
    }
}
