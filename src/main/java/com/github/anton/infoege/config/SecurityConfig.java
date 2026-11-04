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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry,
                                                   LoginAttemptService loginAttemptService) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
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
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
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
