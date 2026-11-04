package com.github.anton.infoege.task;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Ответ с файлом задания. Картинки отдаются inline (для условия), остальное как вложение.
 * Права на файл проверяет вызывающий контроллер.
 */
public final class FileDownload {

    private FileDownload() {
    }

    public static ResponseEntity<InputStreamResource> of(TaskFile file, InputStream content) {
        ContentDisposition disposition = (file.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .body(new InputStreamResource(content));
    }
}
