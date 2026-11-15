package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Сложность видна во время решения подборки, но не варианта (он как экзамен). */
class TrainerDifficultyTest extends AttemptTestSupport {

    private String trainerPage(User student, Assignment assignment) throws Exception {
        String location = mvc.perform(post("/student/assignments/{id}/start", assignment.getId())
                        .with(asStudent(student.getLogin())).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        return mvc.perform(get(location).with(asStudent(student.getLogin())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void collectionShowsDifficulty() throws Exception {
        User student = student();
        Task task = task(3, 1, 1, "7");

        String page = trainerPage(student, assign(variant(null, task), student));

        assertThat(page).contains("diff-3", "class=\"difficulty difficulty-3\"", "Сложность 3 из 5", "difficulty-legend");
    }

    @Test
    void variantHidesDifficulty() throws Exception {
        User student = student();
        Task task = task(3, 1, 1, "7");

        String page = trainerPage(student, assign(variant(60, task), student));

        assertThat(page).doesNotContain("diff-3", "difficulty-3", "Сложность 3 из 5", "difficulty-legend");
    }
}
