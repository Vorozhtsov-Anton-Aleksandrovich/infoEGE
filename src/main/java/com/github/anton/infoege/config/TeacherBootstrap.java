package com.github.anton.infoege.config;

import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Создаёт единственный аккаунт преподавателя из переменных окружения при первом запуске.
 * Если преподаватель уже есть, ничего не делает: смена пароля через ENV не предусмотрена.
 */
@Component
public class TeacherBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TeacherBootstrap.class);
    static final int MIN_STRONG_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeacherProperties properties;

    public TeacherBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            TeacherProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.TEACHER)) {
            return;
        }
        if (!StringUtils.hasText(properties.login()) || !StringUtils.hasText(properties.password())) {
            throw new IllegalStateException(
                    "No teacher account exists: set TEACHER_LOGIN and TEACHER_PASSWORD environment variables");
        }
        if (properties.requireStrongPassword()) {
            checkStrong(properties.login().trim(), properties.password());
        }
        String displayName = StringUtils.hasText(properties.displayName()) ? properties.displayName() : "Преподаватель";
        User teacher = new User(Role.TEACHER, displayName, properties.login().trim(),
                passwordEncoder.encode(properties.password()));
        userRepository.save(teacher);
        log.info("Teacher account '{}' created", teacher.getLogin());
    }

    private static void checkStrong(String login, String password) {
        if (password.length() < MIN_STRONG_PASSWORD_LENGTH || password.equalsIgnoreCase(login)) {
            throw new IllegalStateException("TEACHER_PASSWORD is too weak: use at least "
                    + MIN_STRONG_PASSWORD_LENGTH + " characters, different from TEACHER_LOGIN");
        }
    }
}
