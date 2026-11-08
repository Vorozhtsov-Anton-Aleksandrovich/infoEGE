package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Режим «Проверить ответ»: вердикт сразу, исправлять можно, проверки считаются, в зачёт идёт последний ответ. */
class InstantCheckTest extends AttemptTestSupport {

    @Test
    void checkShowsVerdictCountsChecksAndGradesLastAnswer() throws Exception {
        User student = student();
        Task t = task(1, 1, 1, "42");
        Assignment assignment = assign(variant(null, t), student, ShowResultsMode.INSTANT);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());
        String api = "/trainer/attempts/" + attempt.getId();

        String page = mvc.perform(get(api).with(asStudent(student.getLogin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("data-mode=\"instant\"", "Проверить ответ").doesNotContain("Сохранить ответ");

        check(student, api, t, "[[\"41\"]]")
                .andExpect(jsonPath("$.verdict").value("wrong"))
                .andExpect(jsonPath("$.checks").value(1))
                .andExpect(jsonPath("$.answerSnapshot").doesNotExist());
        // Тот же ответ ещё раз — не новая попытка.
        check(student, api, t, "[[\"41\"]]").andExpect(jsonPath("$.checks").value(1));
        check(student, api, t, "[[\" 42 \"]]")
                .andExpect(jsonPath("$.verdict").value("correct"))
                .andExpect(jsonPath("$.points").value(2))
                .andExpect(jsonPath("$.checks").value(2));

        String reloaded = mvc.perform(get(api).with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reloaded).contains("task-btn status-correct", "Проверок: 2");

        attemptService.finish(attempt.getId(), student.getId());
        AttemptAnswer answer = answerRepository.findByAttemptIdAndTaskId(attempt.getId(), t.getId()).orElseThrow();
        assertThat(answer.getCheckCount()).isEqualTo(2);
        assertThat(answer.getIsCorrect()).isTrue();
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getScore()).isEqualTo(2);
    }

    @Test
    void checkIsRejectedOutsideInstantMode() throws Exception {
        User student = student();
        Task t = task(1, 1, 1, "42");
        Assignment assignment = assign(variant(null, t), student);
        Attempt attempt = attemptService.start(assignment.getId(), student.getId());

        check(student, "/trainer/attempts/" + attempt.getId(), t, "[[\"42\"]]").andExpect(status().isBadRequest());
        assertThat(answerRepository.findByAttemptIdAndTaskId(attempt.getId(), t.getId()).orElseThrow().getAnswer()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions check(User student, String api, Task t, String cells)
            throws Exception {
        return mvc.perform(post(api + "/check").with(asStudent(student.getLogin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(t.getId(), cells)));
    }
}
