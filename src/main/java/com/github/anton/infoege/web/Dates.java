package com.github.anton.infoege.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Перевод между Instant в БД и местным временем в интерфейсе.
 * В шаблонах доступен как {@code ${@dates.format(x)}}.
 */
@Component("dates")
public class Dates {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ZoneId zone;

    public Dates(@Value("${app.time-zone}") ZoneId zone) {
        this.zone = zone;
    }

    public String format(Instant instant) {
        return instant == null ? "" : DISPLAY.format(instant.atZone(zone));
    }

    public Instant toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).toInstant();
    }
}
