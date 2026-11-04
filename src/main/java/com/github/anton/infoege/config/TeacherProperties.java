package com.github.anton.infoege.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.teacher")
public record TeacherProperties(String login, String password, String displayName,
                                boolean requireStrongPassword) {
}
