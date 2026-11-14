package com.github.anton.infoege.config;

import com.github.anton.infoege.security.LoginAttemptService;
import com.github.anton.infoege.security.LoginThrottleFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableConfigurationProperties(TeacherProperties.class)
public class SecurityConfig {

    /**
     * Все скрипты и стили — свои файлы, инлайн-кода нет. img-src data: нужен для пустой иконки сайта.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /**
     * Воркер, в котором Pyodide выполняет код ученика из курсов. WebAssembly и eval внутри Pyodide требуют
     * 'wasm-unsafe-eval' и 'unsafe-eval'. Послабление действует только в самом воркере: у него нет доступа
     * к DOM страницы, а у страницы остаётся строгая политика выше.
     */
    static final String PY_WORKER_PATH = "/js/py-worker.js";
    static final String PY_WORKER_CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry,
                                                   LoginAttemptService loginAttemptService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        // Статика курсов: Python в браузере и редактор кода. Ничего личного, кэшируется надолго.
                        .requestMatchers("/pyodide/**", "/webjars/**").permitAll()
                        .requestMatchers("/invite/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/teacher/**", "/teacher").hasRole("TEACHER")
                        .requestMatchers("/student/**", "/student", "/trainer/**").hasRole("STUDENT")
                        .anyRequest().authenticated())
                .addFilterBefore(new LoginThrottleFilter(loginAttemptService), UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginSuccessHandler(loginAttemptService))
                        .failureHandler(loginFailureHandler(loginAttemptService))
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout"))
                // Реестр сессий нужен, чтобы деактивация ученика сразу завершала его открытые сессии.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/login?expired"))
                // Токен приглашения в адресе не должен утекать в Referer.
                .headers(headers -> headers
                        .addHeaderWriter(contentSecurityPolicy())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    /** Строгая CSP везде, кроме скрипта воркера Python (там своя, см. {@link #PY_WORKER_CONTENT_SECURITY_POLICY}). */
    private static HeaderWriter contentSecurityPolicy() {
        RequestMatcher worker = PathPatternRequestMatcher.withDefaults().matcher(PY_WORKER_PATH);
        return (request, response) -> response.setHeader("Content-Security-Policy",
                worker.matches(request) ? PY_WORKER_CONTENT_SECURITY_POLICY : CONTENT_SECURITY_POLICY);
    }

    /** Успешный вход сбрасывает счётчик неудач для пары «логин + IP». */
    private static AuthenticationSuccessHandler loginSuccessHandler(LoginAttemptService loginAttemptService) {
        SimpleUrlAuthenticationSuccessHandler target = new SimpleUrlAuthenticationSuccessHandler("/");
        target.setAlwaysUseDefaultTargetUrl(true);
        return (request, response, authentication) -> {
            loginAttemptService.recordSuccess(request.getRemoteAddr(), request.getParameter("username"));
            target.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private static AuthenticationFailureHandler loginFailureHandler(LoginAttemptService loginAttemptService) {
        SimpleUrlAuthenticationFailureHandler target = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            loginAttemptService.recordFailure(request.getRemoteAddr(), request.getParameter("username"));
            target.onAuthenticationFailure(request, response, exception);
        };
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
