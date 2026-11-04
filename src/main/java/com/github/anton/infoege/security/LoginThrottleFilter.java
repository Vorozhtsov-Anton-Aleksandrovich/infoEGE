package com.github.anton.infoege.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Не пускает к проверке пароля, если лимит неудачных попыток исчерпан.
 * IP берётся из request.getRemoteAddr(): в prod за Caddy его подставляет RemoteIpValve из X-Forwarded-For.
 */
public class LoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleFilter.class);

    private final LoginAttemptService loginAttemptService;

    public LoginThrottleFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && (request.getContextPath() + "/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        String login = request.getParameter("username");
        if (loginAttemptService.isBlocked(ip, login)) {
            log.warn("Login blocked for ip={} login={}", ip, login);
            response.sendRedirect(request.getContextPath() + "/login?blocked");
            return;
        }
        chain.doFilter(request, response);
    }
}
