package com.github.anton.infoege.web;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import com.github.anton.infoege.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        if (userRepository.findByLogin("student1").isEmpty()) {
            userRepository.save(new User(Role.STUDENT, "Ученик", "student1", passwordEncoder.encode("student-pass")));
        }
        if (userRepository.findByLogin("inactive").isEmpty()) {
            User inactive = new User(Role.STUDENT, "Бывший", "inactive", passwordEncoder.encode("student-pass"));
            inactive.setActive(false);
            userRepository.save(inactive);
        }
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/teacher"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void teacherCanLogIn() throws Exception {
        mvc.perform(formLogin("/login").user(TEACHER_LOGIN).password(TEACHER_PASSWORD))
                .andExpect(authenticated().withRoles("TEACHER"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/login").user(TEACHER_LOGIN).password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        mvc.perform(formLogin("/login").user("inactive").password("student-pass"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/login").param("username", TEACHER_LOGIN).param("password", TEACHER_PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void homeRedirectsByRole() throws Exception {
        mvc.perform(formLogin("/login").user("student1").password("student-pass"))
                .andExpect(authenticated().withRoles("STUDENT"));

        var teacher = new AppUserDetails(userRepository.findByLogin(TEACHER_LOGIN).orElseThrow());
        mvc.perform(get("/").with(user(teacher)))
                .andExpect(redirectedUrl("/teacher"));

        var student = new AppUserDetails(userRepository.findByLogin("student1").orElseThrow());
        mvc.perform(get("/").with(user(student)))
                .andExpect(redirectedUrl("/student"));
    }

    @Test
    void studentCannotOpenTeacherPages() throws Exception {
        mvc.perform(get("/teacher").with(user("student1").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCannotOpenStudentPages() throws Exception {
        mvc.perform(get("/student").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherHomeRendersLayout() throws Exception {
        var teacher = new AppUserDetails(userRepository.findByLogin(TEACHER_LOGIN).orElseThrow());
        mvc.perform(get("/teacher").with(user(teacher)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Анна Петровна")));
    }
}
