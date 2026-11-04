package com.github.anton.infoege.security;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.web.LoggingErrorAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@ExtendWith(OutputCaptureExtension.class)
class HardeningTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginIsBlockedAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        String login = "brute-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(new User(Role.STUDENT, "Жертва", login, passwordEncoder.encode("right-password")));
        RequestPostProcessor attacker = remoteAddr("203.0.113.7");

        for (int i = 0; i < 5; i++) {
            login(login, "wrong-" + i, attacker)
                    .andExpect(redirectedUrl("/login?error"));
        }
        login(login, "right-password", attacker)
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?blocked"));

        // С другого адреса ученик входит: посторонний не может заблокировать ему вход.
        login(login, "right-password", remoteAddr("198.51.100.20"))
                .andExpect(authenticated());
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void serverErrorsAreLoggedWithId(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/teacher/broken");
        request.setAttribute("jakarta.servlet.error.status_code", 500);
        request.setAttribute("jakarta.servlet.error.request_uri", "/teacher/broken");
        request.setAttribute("jakarta.servlet.error.exception", new IllegalStateException("boom-for-test"));

        Map<String, Object> attributes = new LoggingErrorAttributes()
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(attributes.get("errorId")).asString().hasSize(8);
        assertThat(output.getOut()).contains("Server error id=" + attributes.get("errorId"))
                .contains("/teacher/broken")
                .contains("boom-for-test");
    }

    @Test
    void clientErrorsAreNotLoggedAsServerErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");
        request.setAttribute("jakarta.servlet.error.status_code", 404);

        Map<String, Object> attributes = new LoggingErrorAttributes()
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(attributes).doesNotContainKey("errorId");
    }

    private ResultActions login(String login, String password, RequestPostProcessor from) throws Exception {
        return mvc.perform(post("/login").param("username", login).param("password", password).with(csrf()).with(from));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
