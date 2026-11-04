package com.github.anton.infoege.taskset;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskForm;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskSetTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TaskSetService taskSetService;

    @Autowired
    TaskService taskService;

    @Autowired
    AssignmentService assignmentService;

    @Test
    void collectionKeepsManualOrder() throws Exception {
        Task a = task(5);
        Task b = task(1);
        Task c = task(3);
        TaskSet set = taskSetService.create(form(TaskSetType.COLLECTION, null));

        mvc.perform(post("/teacher/sets/{id}/tasks", set.getId()).with(asTeacher()).with(csrf())
                        .param("taskIds", a.getId().toString(), b.getId().toString(), c.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(order(set)).containsExactly(a.getId(), b.getId(), c.getId());

        mvc.perform(post("/teacher/sets/{id}/tasks/{t}/move", set.getId(), c.getId()).param("delta", "-1")
                .with(asTeacher()).with(csrf()));
        assertThat(order(set)).containsExactly(a.getId(), c.getId(), b.getId());

        mvc.perform(post("/teacher/sets/{id}/tasks/{t}/move", set.getId(), a.getId()).param("delta", "-1")
                .with(asTeacher()).with(csrf()));
        assertThat(order(set)).containsExactly(a.getId(), c.getId(), b.getId());

        mvc.perform(post("/teacher/sets/{id}/tasks/{t}/remove", set.getId(), c.getId()).with(asTeacher()).with(csrf()));
        assertThat(order(set)).containsExactly(a.getId(), b.getId());
        assertThat(taskSetService.get(set.getId()).getItems()).extracting(TaskSetItem::getPosition).containsExactly(0, 1);
    }

    @Test
    void variantIsOrderedByEgeNumber() {
        Task t27 = task(27);
        Task t1 = task(1);
        Task t15 = task(15);
        TaskSet set = taskSetService.create(form(TaskSetType.VARIANT, 235));

        taskSetService.addTasks(set.getId(), List.of(t27.getId(), t1.getId(), t15.getId()));
        assertThat(order(set)).containsExactly(t1.getId(), t15.getId(), t27.getId());

        taskSetService.move(set.getId(), t27.getId(), -1);
        assertThat(order(set)).containsExactly(t1.getId(), t15.getId(), t27.getId());
    }

    @Test
    void collectionTurnedIntoVariantGetsSorted() {
        Task t9 = task(9);
        Task t2 = task(2);
        TaskSet set = taskSetService.create(form(TaskSetType.COLLECTION, null));
        taskSetService.addTasks(set.getId(), List.of(t9.getId(), t2.getId()));

        taskSetService.update(set.getId(), form(TaskSetType.VARIANT, 60));

        assertThat(order(set)).containsExactly(t2.getId(), t9.getId());
    }

    @Test
    void variantRequiresTimeLimit() throws Exception {
        mvc.perform(post("/teacher/sets").with(asTeacher()).with(csrf())
                        .param("type", "VARIANT").param("title", "Без лимита"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("обязателен лимит времени")));

        mvc.perform(post("/teacher/sets").with(asTeacher()).with(csrf())
                        .param("type", "COLLECTION").param("title", "Подборка без лимита"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/teacher/sets/*"));
    }

    @Test
    void archivedAndDuplicateTasksAreNotAdded() {
        Task active = task(4);
        Task archived = task(4);
        taskService.setArchived(archived.getId(), true);
        TaskSet set = taskSetService.create(form(TaskSetType.COLLECTION, null));

        taskSetService.addTasks(set.getId(), List.of(active.getId(), archived.getId()));
        taskSetService.addTasks(set.getId(), List.of(active.getId()));

        assertThat(order(set)).containsExactly(active.getId());
    }

    @Test
    void copyIsIndependentTemplate() throws Exception {
        Task a = task(1);
        Task b = task(2);
        TaskSet set = taskSetService.create(form(TaskSetType.VARIANT, 90));
        taskSetService.addTasks(set.getId(), List.of(a.getId(), b.getId()));

        mvc.perform(post("/teacher/sets/{id}/copy", set.getId()).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        TaskSet copy = taskSetService.list().stream()
                .filter(s -> s.getTitle().equals(set.getTitle() + " (копия)")).findFirst().orElseThrow();
        assertThat(copy.getId()).isNotEqualTo(set.getId());
        assertThat(copy.getType()).isEqualTo(TaskSetType.VARIANT);
        assertThat(copy.getTimeLimitMinutes()).isEqualTo(90);
        assertThat(order(copy)).containsExactly(a.getId(), b.getId());

        taskSetService.removeTask(copy.getId(), a.getId());
        assertThat(order(set)).containsExactly(a.getId(), b.getId());
    }

    @Test
    void assignedSetCannotBeDeleted() throws Exception {
        TaskSet free = taskSetService.create(form(TaskSetType.COLLECTION, null));
        TaskSet used = taskSetService.create(form(TaskSetType.COLLECTION, null));
        taskSetService.addTasks(used.getId(), List.of(task(1).getId()));
        assignmentService.assign(used.getId(), List.of(student().getId()), null, ShowResultsMode.AFTER_FINISH);

        mvc.perform(post("/teacher/sets/{id}/delete", free.getId()).with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/teacher/sets/{id}/delete", used.getId()).with(asTeacher()).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        assertThat(taskSetService.list()).extracting(TaskSet::getId).contains(used.getId()).doesNotContain(free.getId());
    }

    @Test
    void taskUsedInSetIsArchivedInsteadOfDeleted() {
        Task task = task(6);
        TaskSet set = taskSetService.create(form(TaskSetType.COLLECTION, null));
        taskSetService.addTasks(set.getId(), List.of(task.getId()));

        assertThat(taskService.delete(task.getId())).isFalse();

        assertThat(taskService.get(task.getId()).isArchived()).isTrue();
        assertThat(order(set)).containsExactly(task.getId());
    }

    @Test
    void pagesRenderAndAreTeacherOnly() throws Exception {
        TaskSet set = taskSetService.create(form(TaskSetType.COLLECTION, null));
        taskSetService.addTasks(set.getId(), List.of(task(8).getId()));

        mvc.perform(get("/teacher/sets").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/sets/new").param("type", "VARIANT").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/sets/{id}", set.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(set.getTitle())));
        mvc.perform(get("/teacher/sets/{id}", set.getId()).with(asStudent("set-student"))).andExpect(status().isForbidden());
        mvc.perform(get("/teacher/sets/999999").with(asTeacher())).andExpect(status().isNotFound());
    }

    private List<Long> order(TaskSet set) {
        return taskSetService.get(set.getId()).getItems().stream().map(i -> i.getTask().getId()).toList();
    }

    private TaskSetForm form(TaskSetType type, Integer timeLimit) {
        TaskSetForm form = new TaskSetForm();
        form.setType(type);
        form.setTitle("Работа " + UUID.randomUUID());
        form.setTimeLimitMinutes(timeLimit);
        return form;
    }

    private Task task(int egeNumber) {
        TaskForm form = new TaskForm();
        form.setEgeNumber(egeNumber);
        form.setTitle("Задание " + egeNumber);
        form.setStatementMd("Условие");
        form.setCorrectAnswer("1");
        return taskService.create(form);
    }

    private User student() {
        String login = "s-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(Role.STUDENT, "Ученик", login, "{noop}x"));
    }
}
