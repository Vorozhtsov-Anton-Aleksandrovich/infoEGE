package com.github.anton.infoege.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    @Test
    void blocksLoginFromIpAfterLimit() {
        LoginAttemptService service = new LoginAttemptService(3, 100, Duration.ofMinutes(15));

        for (int i = 0; i < 3; i++) {
            assertThat(service.isBlocked("1.1.1.1", "ivan")).isFalse();
            service.recordFailure("1.1.1.1", "ivan");
        }

        assertThat(service.isBlocked("1.1.1.1", "ivan")).isTrue();
        assertThat(service.isBlocked("1.1.1.1", "IVAN ")).as("логин без учёта регистра").isTrue();
        assertThat(service.isBlocked("2.2.2.2", "ivan")).as("с другого IP вход не заблокирован").isFalse();
        assertThat(service.isBlocked("1.1.1.1", "petr")).isFalse();
    }

    @Test
    void blocksWholeIpAfterIpLimit() {
        LoginAttemptService service = new LoginAttemptService(100, 4, Duration.ofMinutes(15));

        for (int i = 0; i < 4; i++) {
            service.recordFailure("3.3.3.3", "user" + i);
        }

        assertThat(service.isBlocked("3.3.3.3", "someone-else")).isTrue();
        assertThat(service.isBlocked("4.4.4.4", "someone-else")).isFalse();
    }

    @Test
    void successResetsLoginCounter() {
        LoginAttemptService service = new LoginAttemptService(3, 100, Duration.ofMinutes(15));
        service.recordFailure("5.5.5.5", "anna");
        service.recordFailure("5.5.5.5", "anna");

        service.recordSuccess("5.5.5.5", "anna");
        service.recordFailure("5.5.5.5", "anna");

        assertThat(service.isBlocked("5.5.5.5", "anna")).isFalse();
    }

    @Test
    void blockExpiresAfterWindow() throws Exception {
        LoginAttemptService service = new LoginAttemptService(1, 100, Duration.ofMillis(50));
        service.recordFailure("6.6.6.6", "olga");
        assertThat(service.isBlocked("6.6.6.6", "olga")).isTrue();

        Thread.sleep(80);
        service.cleanUp();

        assertThat(service.isBlocked("6.6.6.6", "olga")).isFalse();
    }
}
