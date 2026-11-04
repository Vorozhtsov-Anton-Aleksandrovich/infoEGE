package com.github.anton.infoege.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ограничение попыток входа в памяти процесса (приложение работает в одном экземпляре).
 * Считаются неудачные попытки за окно: отдельно для пары «логин + IP» и для IP целиком.
 * Блокировка только по логину не используется, чтобы посторонний не мог заблокировать вход ученику.
 */
@Service
public class LoginAttemptService {

    private final int maxPerLogin;
    private final int maxPerIp;
    private final Duration window;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${app.login.max-failures-per-login:5}") int maxPerLogin,
                               @Value("${app.login.max-failures-per-ip:30}") int maxPerIp,
                               @Value("${app.login.window:PT15M}") Duration window) {
        this.maxPerLogin = maxPerLogin;
        this.maxPerIp = maxPerIp;
        this.window = window;
    }

    private record Counter(Instant windowStart, int failures) {
    }

    public boolean isBlocked(String ip, String login) {
        Instant now = Instant.now();
        return failures(loginKey(ip, login), now) >= maxPerLogin || failures(ipKey(ip), now) >= maxPerIp;
    }

    public void recordFailure(String ip, String login) {
        Instant now = Instant.now();
        increment(loginKey(ip, login), now);
        increment(ipKey(ip), now);
    }

    public void recordSuccess(String ip, String login) {
        counters.remove(loginKey(ip, login));
    }

    /** Удаляет истёкшие счётчики, чтобы карта не росла бесконечно. */
    @Scheduled(fixedDelay = 600_000)
    public void cleanUp() {
        Instant now = Instant.now();
        counters.entrySet().removeIf(e -> isExpired(e.getValue(), now));
    }

    private int failures(String key, Instant now) {
        Counter counter = counters.get(key);
        return counter == null || isExpired(counter, now) ? 0 : counter.failures();
    }

    private void increment(String key, Instant now) {
        counters.compute(key, (k, c) -> c == null || isExpired(c, now) ? new Counter(now, 1) : new Counter(c.windowStart(), c.failures() + 1));
    }

    private boolean isExpired(Counter counter, Instant now) {
        return !now.isBefore(counter.windowStart().plus(window));
    }

    private static String loginKey(String ip, String login) {
        return "login:" + ip + ":" + (login == null ? "" : login.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }
}
