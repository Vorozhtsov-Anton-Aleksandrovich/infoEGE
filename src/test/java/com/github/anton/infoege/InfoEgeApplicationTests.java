package com.github.anton.infoege;

import com.github.anton.infoege.config.TeacherBootstrap;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class InfoEgeApplicationTests extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    TeacherBootstrap teacherBootstrap;

    @Test
    void teacherIsCreatedFromProperties() {
        User teacher = userRepository.findByLogin(TEACHER_LOGIN).orElseThrow();

        assertThat(teacher.getRole()).isEqualTo(Role.TEACHER);
        assertThat(teacher.getDisplayName()).isEqualTo("Анна Петровна");
        assertThat(teacher.isActive()).isTrue();
        assertThat(teacher.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(TEACHER_PASSWORD, teacher.getPasswordHash())).isTrue();
    }

    @Test
    void bootstrapDoesNotCreateSecondTeacher() throws Exception {
        teacherBootstrap.run(null);

        long teachers = userRepository.findAll().stream().filter(u -> u.getRole() == Role.TEACHER).count();
        assertThat(teachers).isEqualTo(1);
    }
}
