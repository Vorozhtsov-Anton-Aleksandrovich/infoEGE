package com.github.anton.infoege.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.Set;

/**
 * @param dir                  каталог для файлов
 * @param maxFileSize          лимит размера одного файла
 * @param attachmentExtensions разрешённые расширения файлов для скачивания
 * @param imageExtensions      разрешённые расширения картинок в условии
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Path dir,
                                DataSize maxFileSize,
                                Set<String> attachmentExtensions,
                                Set<String> imageExtensions) {
}
