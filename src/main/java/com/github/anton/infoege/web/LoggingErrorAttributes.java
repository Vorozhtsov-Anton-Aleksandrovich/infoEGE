package com.github.anton.infoege.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.UUID;

/**
 * Серверные ошибки (5xx) пишутся в лог с коротким номером, адресом и пользователем.
 * Тот же номер показывается на странице ошибки, чтобы по нему найти запись в логе.
 */
@Component
public class LoggingErrorAttributes extends DefaultErrorAttributes {

    private static final Logger log = LoggerFactory.getLogger(LoggingErrorAttributes.class);

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(request, options);
        if (attributes.get("status") instanceof Integer status && status >= 500) {
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            log.error("Server error id={} status={} path={} user={}", errorId, status, attributes.get("path"),
                    request.getRemoteUser(), getError(request));
            attributes.put("errorId", errorId);
        }
        return attributes;
    }
}
