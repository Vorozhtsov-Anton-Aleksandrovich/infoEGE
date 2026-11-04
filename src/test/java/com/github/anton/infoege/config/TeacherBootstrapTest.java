package com.github.anton.infoege.config;

import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);

    private void run(String login, String password, boolean strong) {
        when(users.existsByRole(Role.TEACHER)).thenReturn(false);
        new TeacherBootstrap(users, NoOpPasswordEncoder.getInstance(),
                new TeacherProperties(login, password, null, strong)).run(null);
    }

    @Test
    void strongModeRejectsShortPassword() {
        assertThatThrownBy(() -> run("ivanova", "teacher", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEACHER_PASSWORD");
        verify(users, never()).save(any());
    }

    @Test
    void strongModeRejectsPasswordEqualToLogin() {
        assertThatThrownBy(() -> run("teacher12345", "TEACHER12345", true))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void strongModeAcceptsLongPassword() {
        assertThatCode(() -> run("ivanova", "k3Jd9-qpLx2-Vm7a", true)).doesNotThrowAnyException();
        verify(users).save(any(User.class));
    }

    @Test
    void devModeAllowsSimplePassword() {
        assertThatCode(() -> run("teacher", "teacher", false)).doesNotThrowAnyException();
        verify(users).save(any(User.class));
    }

    @Test
    void missingCredentialsFail() {
        assertThatThrownBy(() -> run("", "", false)).isInstanceOf(IllegalStateException.class);
    }
}
