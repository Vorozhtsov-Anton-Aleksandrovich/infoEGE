package com.github.anton.infoege.storage;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Проверка загружаемых файлов: белый список расширений и лимит размера.
 * Content-Type определяется по расширению, заголовку от браузера не доверяем.
 */
@Component
public class FileValidator {

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("py", "text/plain; charset=utf-8"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry("zip", "application/zip"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"));

    private static final int MAX_NAME_LENGTH = 255;

    private final StorageProperties properties;

    public FileValidator(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * @return сообщение об ошибке для пользователя или null, если файл подходит
     */
    public String validate(String originalName, long sizeBytes, boolean image) {
        if (originalName == null || originalName.isBlank()) {
            return "У файла нет имени";
        }
        if (originalName.length() > MAX_NAME_LENGTH) {
            return "Слишком длинное имя файла: " + originalName;
        }
        if (sizeBytes <= 0) {
            return "Файл пустой: " + originalName;
        }
        if (sizeBytes > properties.maxFileSize().toBytes()) {
            return "Файл больше " + properties.maxFileSize().toMegabytes() + " МБ: " + originalName;
        }
        Set<String> allowed = image ? properties.imageExtensions() : properties.attachmentExtensions();
        String extension = extension(originalName);
        if (!allowed.contains(extension) || !CONTENT_TYPES.containsKey(extension)) {
            return "Недопустимый тип файла: " + originalName + ". Разрешены: " + String.join(", ", allowed);
        }
        return null;
    }

    public String contentType(String originalName) {
        return CONTENT_TYPES.getOrDefault(extension(originalName), "application/octet-stream");
    }

    /** Убирает путь, который некоторые браузеры передают вместе с именем. */
    public static String cleanName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String name = originalName.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1).trim();
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
