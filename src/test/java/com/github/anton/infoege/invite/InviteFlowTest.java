package com.github.anton.infoege.invite;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InviteFlowTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InviteRepository inviteRepository;

    @Autowired
    InviteService inviteService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void studentRegistersOnceByLink() throws Exception {
        String token = inviteViaUi("Мария Иванова");
        String login = "maria-" + shortId();

        assertThat(inviteRepository.findByTokenHash(token)).as("raw token must not be stored").isEmpty();
        Invite invite = inviteRepository.findByTokenHash(InviteService.hash(token)).orElseThrow();
        assertThat(invite.getExpiresAt()).isAfter(invite.getCreatedAt().plus(InviteService.LIFETIME).minusSeconds(5));

        mvc.perform(get("/invite/{t}", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Мария Иванова")));

        register(token, login, "secret-pass", "secret-pass")
                .andExpect(redirectedUrl("/login?registered"));

        User student = userRepository.findByLogin(login).orElseThrow();
        assertThat(student.getRole()).isEqualTo(Role.STUDENT);
        assertThat(student.getDisplayName()).isEqualTo("Мария Иванова");
        mvc.perform(formLogin("/login").user(login).password("secret-pass")).andExpect(authenticated().withRoles("STUDENT"));

        mvc.perform(get("/invite/{t}", token)).andExpect(content().string(containsString("Ссылка недействительна")));
        register(token, "other-" + shortId(), "secret-pass", "secret-pass")
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void unknownAndExpiredLinksDoNotWork() throws Exception {
        mvc.perform(get("/invite/{t}", "no-such-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ссылка недействительна")));

        String token = inviteViaUi("Просроченный");
        jdbc.update("update invite set expires_at = now() - interval '1 minute' where token_hash = ?", InviteService.hash(token));

        mvc.perform(get("/invite/{t}", token)).andExpect(content().string(containsString("Ссылка недействительна")));
        register(token, "late-" + shortId(), "secret-pass", "secret-pass")
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void validatesLoginAndPassword() throws Exception {
        String token = inviteViaUi("Пётр");
        String taken = "taken-" + shortId();
        userRepository.save(new User(Role.STUDENT, "Другой", taken, passwordEncoder.encode("x")));

        register(token, "ив", "secret-pass", "secret-pass").andExpect(content().string(containsString("От 3 до 32 символов")));
        register(token, "petr-" + shortId(), "short", "short").andExpect(content().string(containsString("Пароль от 8 до 72")));
        register(token, "petr-" + shortId(), "secret-pass", "other-pass").andExpect(content().string(containsString("Пароли не совпадают")));
        register(token, taken.toUpperCase(), "secret-pass", "secret-pass").andExpect(content().string(containsString("логин уже занят")));

        assertThat(inviteRepository.findByTokenHash(InviteService.hash(token)).orElseThrow().getUsedAt()).isNull();
    }

    @Test
    void registrationRequiresCsrf() throws Exception {
        String token = inviteViaUi("Без CSRF");

        mvc.perform(post("/invite/{t}", token).param("login", "nocsrf-" + shortId())
                        .param("password", "secret-pass").param("passwordConfirm", "secret-pass"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetLinkChangesOnlyPasswordAndReplacesPreviousLink() throws Exception {
        String login = "reset-" + shortId();
        register(inviteViaUi("Сброс"), login, "old-password", "old-password");
        User student = userRepository.findByLogin(login).orElseThrow();

        String first = resetViaUi(student.getId());
        String second = resetViaUi(student.getId());

        mvc.perform(get("/invite/{t}", first)).andExpect(content().string(containsString("Ссылка недействительна")));
        mvc.perform(get("/invite/{t}", second))
                .andExpect(content().string(containsString("Новый пароль")))
                .andExpect(content().string(containsString(login)));

        register(second, "ignored-login", "new-password", "new-password").andExpect(redirectedUrl("/login?passwordChanged"));

        assertThat(userRepository.findByLogin("ignored-login")).isEmpty();
        mvc.perform(formLogin("/login").user(login).password("old-password")).andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user(login).password("new-password")).andExpect(authenticated());
    }

    @Test
    void reissuedAndRevokedInvitesStopWorking() throws Exception {
        String old = inviteViaUi("Перевыпуск");
        Invite invite = inviteRepository.findByTokenHash(InviteService.hash(old)).orElseThrow();

        MvcResult result = mvc.perform(post("/teacher/students/invites/{id}/reissue", invite.getId()).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String fresh = tokenFrom(result);

        mvc.perform(get("/invite/{t}", old)).andExpect(content().string(containsString("Ссылка недействительна")));
        mvc.perform(get("/invite/{t}", fresh)).andExpect(content().string(containsString("Перевыпуск")));

        Invite freshInvite = inviteRepository.findByTokenHash(InviteService.hash(fresh)).orElseThrow();
        mvc.perform(post("/teacher/students/invites/{id}/delete", freshInvite.getId()).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/invite/{t}", fresh)).andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void studentCannotManageStudents() throws Exception {
        mvc.perform(get("/teacher/students").with(asStudent("invite-student"))).andExpect(status().isForbidden());
        mvc.perform(post("/teacher/students/invites").param("displayName", "x")
                        .with(asStudent("invite-student")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private String inviteViaUi(String name) throws Exception {
        MvcResult result = mvc.perform(post("/teacher/students/invites").param("displayName", name)
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return tokenFrom(result);
    }

    private String resetViaUi(Long studentId) throws Exception {
        MvcResult result = mvc.perform(post("/teacher/students/{id}/reset-link", studentId).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return tokenFrom(result);
    }

    private static String tokenFrom(MvcResult result) {
        String link = (String) result.getFlashMap().get("inviteLink");
        assertThat(link).contains("/invite/");
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private ResultActions register(String token, String login, String password, String confirm) throws Exception {
        return mvc.perform(post("/invite/{t}", token).with(csrf())
                .param("login", login).param("password", password).param("passwordConfirm", confirm));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
