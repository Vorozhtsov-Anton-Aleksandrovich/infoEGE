package com.github.anton.infoege.task;

import com.github.anton.infoege.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherTaskControllerTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TaskService taskService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskFileRepository fileRepository;

    @Test
    void createTaskWithFilesAndPreviewAsStudent() throws Exception {
        String title = "Анализ файла " + UUID.randomUUID();
        mvc.perform(post("/teacher/tasks").with(asTeacher()).with(csrf())
                        .param("egeNumber", "27")
                        .param("title", title)
                        .param("statementMd", "Найдите пару чисел.\n\n![схема](file:graph.png)")
                        .param("difficulty", "5")
                        .param("maxPoints", "2")
                        .param("scoring", "PER_ROW")
                        .param("answerRows", "2")
                        .param("answerCols", "2")
                        .param("correctAnswer", "31415 92653\n58979 32384")
                        .param("solutionEnabled", "true")
                        .param("solutionMd", "Секретный разбор")
                        .param("topic", "Программирование"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/teacher/tasks/*/edit"));

        Task task = taskRepository.findAll().stream().filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow();
        assertThat(task.getCorrectAnswer()).isEqualTo(List.of(List.of("31415", "92653"), List.of("58979", "32384")));
        assertThat(task.getScoring()).isEqualTo(Scoring.PER_ROW);

        mvc.perform(multipart("/teacher/tasks/{id}/files", task.getId())
                        .file(new MockMultipartFile("files", "27_A.txt", "text/plain", "1 2 3".getBytes()))
                        .file(new MockMultipartFile("files", "27_B.txt", "text/plain", "4 5 6".getBytes()))
                        .param("kind", "ATTACHMENT")
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(multipart("/teacher/tasks/{id}/files", task.getId())
                        .file(new MockMultipartFile("files", "graph.png", "image/png", new byte[]{1, 2, 3}))
                        .param("kind", "IMAGE")
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        List<TaskFile> files = fileRepository.findByTaskIdOrderById(task.getId());
        assertThat(files).extracting(TaskFile::getOriginalName).containsExactly("27_A.txt", "27_B.txt", "graph.png");
        TaskFile image = files.get(2);

        String html = mvc.perform(get("/teacher/tasks/{id}/preview", task.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String studentPart = html.substring(html.indexOf("student-frame"), html.indexOf("teacher-only"));
        assertThat(studentPart)
                .contains("Найдите пару чисел.")
                .contains("src=\"/teacher/tasks/" + task.getId() + "/files/" + image.getId() + "\"")
                .contains("27_A.txt", "27_B.txt")
                .contains("name=\"cell-1-1\"")
                .doesNotContain("31415", "Секретный разбор", "graph.png");
    }

    @Test
    void rejectsAnswerOfWrongShape() throws Exception {
        long before = taskRepository.count();

        mvc.perform(post("/teacher/tasks").with(asTeacher()).with(csrf())
                        .param("egeNumber", "25")
                        .param("title", "Неверная форма")
                        .param("statementMd", "Условие")
                        .param("difficulty", "3")
                        .param("maxPoints", "1")
                        .param("scoring", "ALL_OR_NOTHING")
                        .param("answerRows", "2")
                        .param("answerCols", "2")
                        .param("correctAnswer", "1 2 3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ответ должен быть таблицей 2 × 2")));

        assertThat(taskRepository.count()).isEqualTo(before);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        mvc.perform(post("/teacher/tasks").with(asTeacher()).with(csrf())
                        .param("egeNumber", "0")
                        .param("difficulty", "9"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Укажите название")))
                .andExpect(content().string(containsString("Номер не меньше 1")))
                .andExpect(content().string(containsString("От 1 до 5")));
    }

    @Test
    void updatesTask() throws Exception {
        Task task = createTask(5, "topic-" + UUID.randomUUID(), 2);

        mvc.perform(post("/teacher/tasks/{id}", task.getId()).with(asTeacher()).with(csrf())
                        .param("egeNumber", "6")
                        .param("title", "Новое название")
                        .param("statementMd", "Новое условие")
                        .param("difficulty", "4")
                        .param("maxPoints", "1")
                        .param("scoring", "ALL_OR_NOTHING")
                        .param("answerRows", "1")
                        .param("answerCols", "1")
                        .param("correctAnswer", "  77 "))
                .andExpect(status().is3xxRedirection());

        Task updated = taskService.get(task.getId());
        assertThat(updated.getEgeNumber()).isEqualTo(6);
        assertThat(updated.getTitle()).isEqualTo("Новое название");
        assertThat(updated.getCorrectAnswer()).isEqualTo(List.of(List.of("77")));
        assertThat(updated.getTopic()).isNull();
    }

    @Test
    void filtersByNumberTopicDifficultyAndArchive() throws Exception {
        String topic = "topic-" + UUID.randomUUID();
        Task a = createTask(8, topic, 2);
        Task b = createTask(8, topic, 4);
        Task c = createTask(9, topic, 2);
        Task archived = createTask(8, topic, 2);
        taskService.setArchived(archived.getId(), true);

        assertThat(taskService.search(new TaskFilter(8, topic, null, null)))
                .extracting(Task::getId).containsExactly(a.getId(), b.getId());
        assertThat(taskService.search(new TaskFilter(null, topic, 2, null)))
                .extracting(Task::getId).containsExactly(a.getId(), c.getId());
        assertThat(taskService.search(new TaskFilter(null, topic, null, TaskFilter.Archive.ARCHIVED)))
                .extracting(Task::getId).containsExactly(archived.getId());
        assertThat(taskService.search(new TaskFilter(null, topic, null, TaskFilter.Archive.ALL))).hasSize(4);

        mvc.perform(get("/teacher/tasks").param("egeNumber", "9").param("topic", topic).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/teacher/tasks/" + c.getId() + "/edit")))
                .andExpect(content().string(not(containsString("/teacher/tasks/" + a.getId() + "/edit"))));
    }

    @Test
    void rejectsForbiddenDuplicateAndLargeFiles() throws Exception {
        Task task = createTask(3, null, 1);
        taskService.addFiles(task.getId(), List.of(new MockMultipartFile("files", "data.txt", "text/plain", "x".getBytes())),
                FileKind.ATTACHMENT);

        mvc.perform(multipart("/teacher/tasks/{id}/files", task.getId())
                        .file(new MockMultipartFile("files", "virus.exe", "application/octet-stream", "MZ".getBytes()))
                        .file(new MockMultipartFile("files", "data.txt", "text/plain", "y".getBytes()))
                        .file(new MockMultipartFile("files", "big.txt", "text/plain", new byte[2 * 1024 * 1024]))
                        .file(new MockMultipartFile("files", "page.svg", "image/svg+xml", "<svg/>".getBytes()))
                        .param("kind", "ATTACHMENT")
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("fileErrors", hasSize(4)));

        assertThat(fileRepository.findByTaskIdOrderById(task.getId())).hasSize(1);
    }

    @Test
    void downloadsAttachmentAndShowsImageInline() throws Exception {
        Task task = createTask(10, null, 1);
        taskService.addFiles(task.getId(), List.of(
                new MockMultipartFile("files", "10.docx", "x/y", "docx-bytes".getBytes())), FileKind.ATTACHMENT);
        taskService.addFiles(task.getId(), List.of(
                new MockMultipartFile("files", "pic.png", "x/y", new byte[]{9, 9})), FileKind.IMAGE);
        List<TaskFile> files = fileRepository.findByTaskIdOrderById(task.getId());

        mvc.perform(get("/teacher/tasks/{id}/files/{fid}", task.getId(), files.get(0).getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes("docx-bytes".getBytes()));

        mvc.perform(get("/teacher/tasks/{id}/files/{fid}", task.getId(), files.get(1).getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void fileOfAnotherTaskIsNotFound() throws Exception {
        Task owner = createTask(1, null, 1);
        Task other = createTask(1, null, 1);
        taskService.addFiles(owner.getId(), List.of(new MockMultipartFile("files", "a.txt", "x", "a".getBytes())),
                FileKind.ATTACHMENT);
        TaskFile file = fileRepository.findByTaskIdOrderById(owner.getId()).get(0);

        mvc.perform(get("/teacher/tasks/{id}/files/{fid}", other.getId(), file.getId()).with(asTeacher()))
                .andExpect(status().isNotFound());
    }

    @Test
    void markdownPreviewIsSanitized() throws Exception {
        mvc.perform(post("/teacher/tasks/markdown-preview").with(asTeacher()).with(csrf())
                        .param("statementMd", "**ok** <script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>ok</strong>")))
                .andExpect(content().string(not(containsString("<script"))));
    }

    @Test
    void deleteRemovesTaskAndStoredFiles() throws Exception {
        Task task = createTask(2, null, 1);
        taskService.addFiles(task.getId(), List.of(new MockMultipartFile("files", "a.txt", "x", "a".getBytes())),
                FileKind.ATTACHMENT);
        String key = fileRepository.findByTaskIdOrderById(task.getId()).get(0).getStorageKey();
        assertThat(Files.exists(Path.of("target/test-files", key))).isTrue();

        mvc.perform(post("/teacher/tasks/{id}/delete", task.getId()).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(taskRepository.findById(task.getId())).isEmpty();
        assertThat(Files.exists(Path.of("target/test-files", key))).isFalse();
    }

    @Test
    void studentHasNoAccessToTaskBank() throws Exception {
        Task task = createTask(4, null, 1);
        taskService.addFiles(task.getId(), List.of(new MockMultipartFile("files", "a.txt", "x", "a".getBytes())),
                FileKind.ATTACHMENT);
        TaskFile file = fileRepository.findByTaskIdOrderById(task.getId()).get(0);

        mvc.perform(get("/teacher/tasks").with(asStudent("bank-student")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/teacher/tasks/{id}/preview", task.getId()).with(asStudent("bank-student")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/teacher/tasks/{id}/files/{fid}", task.getId(), file.getId()).with(asStudent("bank-student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pastedImagesGetUniqueNames() throws Exception {
        Task task = createTask(13, null, 1);

        for (String expected : List.of("image.png", "image-2.png")) {
            mvc.perform(multipart("/teacher/tasks/{id}/images", task.getId())
                            .file(new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2}))
                            .with(asTeacher()).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(expected));
        }
        mvc.perform(multipart("/teacher/tasks/{id}/images", task.getId())
                        .file(new MockMultipartFile("file", "", "image/jpeg", new byte[]{1}))
                        .with(asTeacher()).with(csrf()))
                .andExpect(jsonPath("$.name").value("image.jpg"));

        assertThat(fileRepository.findByTaskIdOrderById(task.getId()))
                .allMatch(TaskFile::isImage)
                .extracting(TaskFile::getOriginalName).containsExactly("image.png", "image-2.png", "image.jpg");
    }

    @Test
    void pastedNonImageIsRejected() throws Exception {
        Task task = createTask(14, null, 1);

        mvc.perform(multipart("/teacher/tasks/{id}/images", task.getId())
                        .file(new MockMultipartFile("file", "data.txt", "text/plain", "x".getBytes()))
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Недопустимый тип")));
        mvc.perform(multipart("/teacher/tasks/{id}/images", task.getId())
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                        .with(asStudent("paste-student")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(fileRepository.findByTaskIdOrderById(task.getId())).isEmpty();
    }

    @Test
    void solutionIsSavedOnlyWhenEnabled() throws Exception {
        Task task = createTask(15, null, 1);
        String url = "/teacher/tasks/" + task.getId();

        mvc.perform(post(url).with(asTeacher()).with(csrf()).params(baseParams())
                .param("solutionEnabled", "true").param("solutionMd", "Разбор есть"));
        assertThat(taskService.get(task.getId()).getSolutionMd()).isEqualTo("Разбор есть");

        // Галочка снята: текст в поле остаётся у браузера, но разбор не сохраняется.
        mvc.perform(post(url).with(asTeacher()).with(csrf()).params(baseParams())
                .param("solutionMd", "Разбор есть"));
        assertThat(taskService.get(task.getId()).getSolutionMd()).isNull();
    }

    private static MultiValueMap<String, String> baseParams() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("egeNumber", "15");
        params.add("title", "С разбором и без");
        params.add("statementMd", "Условие");
        params.add("difficulty", "3");
        params.add("maxPoints", "1");
        params.add("scoring", "ALL_OR_NOTHING");
        params.add("answerRows", "1");
        params.add("answerCols", "1");
        params.add("correctAnswer", "1");
        return params;
    }

    @Test
    void pagesRender() throws Exception {
        Task task = createTask(7, "Графы", 3);

        mvc.perform(get("/teacher/tasks").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/tasks/new").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/tasks/{id}/edit", task.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Графы")));
        mvc.perform(get("/teacher/tasks/999999/edit").with(asTeacher())).andExpect(status().isNotFound());
    }

    private Task createTask(int egeNumber, String topic, int difficulty) {
        TaskForm form = new TaskForm();
        form.setEgeNumber(egeNumber);
        form.setTitle("Задание " + egeNumber);
        form.setStatementMd("Условие");
        form.setDifficulty(difficulty);
        form.setCorrectAnswer("42");
        form.setTopic(topic);
        return taskService.create(form);
    }
}
