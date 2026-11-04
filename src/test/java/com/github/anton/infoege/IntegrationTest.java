package com.github.anton.infoege;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Базовый класс интеграционных тестов: один контейнер PostgreSQL на все тесты.
 */
@SpringBootTest(properties = {
        "app.teacher.login=teacher",
        "app.teacher.password=teacher-pass",
        "app.teacher.display-name=Анна Петровна"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    public static final String TEACHER_LOGIN = "teacher";
    public static final String TEACHER_PASSWORD = "teacher-pass";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
