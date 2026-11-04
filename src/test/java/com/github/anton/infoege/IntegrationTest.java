package com.github.anton.infoege;

import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Базовый класс интеграционных тестов: один контейнер PostgreSQL на все тесты.
 */
@SpringBootTest(properties = {
        "app.teacher.login=teacher",
        "app.teacher.password=teacher-pass",
        "app.teacher.display-name=Анна Петровна",
        "app.storage.dir=target/test-files",
        "app.storage.max-file-size=1MB"
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

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected RequestPostProcessor asTeacher() {
        return user(new AppUserDetails(userRepository.findByLogin(TEACHER_LOGIN).orElseThrow()));
    }

    protected RequestPostProcessor asStudent(String login) {
        User student = userRepository.findByLogin(login)
                .orElseGet(() -> userRepository.save(new User(Role.STUDENT, "Ученик " + login, login,
                        passwordEncoder.encode("student-pass"))));
        return user(new AppUserDetails(student));
    }
}
