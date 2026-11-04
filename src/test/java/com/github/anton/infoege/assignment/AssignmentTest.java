package com.github.anton.infoege.assignment;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.student.StudentService;
import com.github.anton.infoege.task.TaskForm;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetForm;
import com.github.anton.infoege.taskset.TaskSetService;
import com.github.anton.infoege.taskset.TaskSetType;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TaskService taskService;

    @Autowired
    TaskSetService taskSetService;

    @Autowired
    AssignmentService assignmentService;

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    StudentService studentService;

    @Autowired
    SessionRegistry sessionRegistry;

    /** Критерий готовности этапа: ученик регистрируется по ссылке и видит назначенную работу. */
    @Test
    void studentRegistersByLinkAndSeesAssignedWork() throws Exception {
        MvcResult invite = mvc.perform(post("/teacher/students/invites").param("displayName", "Олег Смирнов")
                .with(asTeacher()).with(csrf())).andReturn();
        String link = (String) invite.getFlashMap().get("inviteLink");
        String token = link.substring(link.lastIndexOf('/') + 1);
        String login = "oleg-" + shortId();

        mvc.perform(post("/invite/{t}", token).with(csrf())
                        .param("login", login).param("password", "oleg-password").param("passwordConfirm", "oleg-password"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(formLogin("/login").user(login).password("oleg-password")).andExpect(authenticated());
        User student = userRepository.findByLogin(login).orElseThrow();

        TaskSet set = setWithTasks("Вариант для Олега " + shortId(), 2);
        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                        .param("studentIds", student.getId().toString())
                        .param("deadline", "2099-05-20T18:00")
                        .param("showResultsMode", "AFTER_DEADLINE"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/student").with(asStudent(login)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(set.getTitle())))
                .andExpect(content().string(containsString("20.05.2099 18:00")))
                .andExpect(content().string(containsString("заданий: <span>2</span>")));
    }

    @Test
    void assignsToSeveralStudentsAndSkipsDuplicates() throws Exception {
        User a = student();
        User b = student();
        TaskSet set = setWithTasks("Подборка " + shortId(), 1);

        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                .param("studentIds", a.getId().toString(), b.getId().toString())
                .param("showResultsMode", "AFTER_FINISH"));
        var result = assignmentService.assign(set.getId(), List.of(a.getId()), null, ShowResultsMode.TEACHER_ONLY);

        assertThat(assignmentService.forTaskSet(set.getId())).extracting(x -> x.getStudent().getId())
                .containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(result.created()).isZero();
        assertThat(result.alreadyAssigned()).hasSize(1);
    }

    @Test
    void cannotAssignToTeacherOrInactiveStudent() throws Exception {
        TaskSet set = setWithTasks("Подборка " + shortId(), 1);
        User inactive = student();
        studentService.setActive(inactive.getId(), false);
        Long teacherId = userRepository.findByLogin(TEACHER_LOGIN).orElseThrow().getId();

        for (Long id : List.of(teacherId, inactive.getId(), 999999L)) {
            mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                            .param("studentIds", id.toString()).param("showResultsMode", "AFTER_FINISH"))
                    .andExpect(status().isNotFound());
        }
        assertThat(assignmentService.forTaskSet(set.getId())).isEmpty();
    }

    @Test
    void validatesDeadline() throws Exception {
        User s = student();
        TaskSet set = setWithTasks("Подборка " + shortId(), 1);

        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                        .param("studentIds", s.getId().toString()).param("showResultsMode", "AFTER_DEADLINE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("нужен срок")));
        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                        .param("studentIds", s.getId().toString()).param("showResultsMode", "AFTER_FINISH")
                        .param("deadline", LocalDateTime.now().minusDays(1).withNano(0).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Срок уже прошёл")));
        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                        .param("showResultsMode", "AFTER_FINISH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Выберите хотя бы одного ученика")));

        assertThat(assignmentService.forTaskSet(set.getId())).isEmpty();
    }

    @Test
    void emptySetCannotBeAssigned() throws Exception {
        User s = student();
        TaskSet set = setWithTasks("Пустая " + shortId(), 0);

        mvc.perform(post("/teacher/sets/{id}/assign", set.getId()).with(asTeacher()).with(csrf())
                        .param("studentIds", s.getId().toString()).param("showResultsMode", "AFTER_FINISH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("В работе нет заданий")));
    }

    @Test
    void studentSeesOnlyOwnAssignments() throws Exception {
        User mine = student();
        User other = student();
        TaskSet myWork = setWithTasks("Моя работа " + shortId(), 1);
        TaskSet otherWork = setWithTasks("Чужая работа " + shortId(), 1);
        assignmentService.assign(myWork.getId(), List.of(mine.getId()), null, ShowResultsMode.AFTER_FINISH);
        assignmentService.assign(otherWork.getId(), List.of(other.getId()), null, ShowResultsMode.AFTER_FINISH);

        mvc.perform(get("/student").with(asStudent(mine.getLogin())))
                .andExpect(content().string(containsString(myWork.getTitle())))
                .andExpect(content().string(not(containsString(otherWork.getTitle()))));
    }

    @Test
    void assignmentIsRemovedOnlyFromItsSet() throws Exception {
        User s = student();
        TaskSet set = setWithTasks("Подборка " + shortId(), 1);
        TaskSet otherSet = setWithTasks("Другая " + shortId(), 1);
        assignmentService.assign(set.getId(), List.of(s.getId()), null, ShowResultsMode.AFTER_FINISH);
        Long assignmentId = assignmentService.forTaskSet(set.getId()).get(0).getId();

        mvc.perform(post("/teacher/sets/{id}/assignments/{a}/delete", otherSet.getId(), assignmentId)
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/teacher/sets/{id}/assignments/{a}/delete", set.getId(), assignmentId)
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(assignmentRepository.findById(assignmentId)).isEmpty();
    }

    @Test
    void deactivationExpiresOpenSessions() throws Exception {
        User s = student();
        AppUserDetails principal = new AppUserDetails(s);
        String sessionId = "session-" + shortId();
        sessionRegistry.registerNewSession(sessionId, principal);

        mvc.perform(post("/teacher/students/{id}/active", s.getId()).param("active", "false")
                        .with(asTeacher()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
        assertThat(userRepository.findById(s.getId()).orElseThrow().isActive()).isFalse();
        sessionRegistry.removeSessionInformation(sessionId);
    }

    @Test
    void studentPagesRender() throws Exception {
        User s = student();
        mvc.perform(get("/teacher/students").with(asTeacher())).andExpect(status().isOk());
        mvc.perform(get("/teacher/students/{id}", s.getId()).with(asTeacher()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(s.getLogin())));
        Long teacherId = userRepository.findByLogin(TEACHER_LOGIN).orElseThrow().getId();
        mvc.perform(get("/teacher/students/{id}", teacherId).with(asTeacher())).andExpect(status().isNotFound());
    }

    private TaskSet setWithTasks(String title, int count) {
        TaskSetForm form = new TaskSetForm();
        form.setType(TaskSetType.COLLECTION);
        form.setTitle(title);
        TaskSet set = taskSetService.create(form);
        for (int i = 1; i <= count; i++) {
            TaskForm task = new TaskForm();
            task.setEgeNumber(i);
            task.setTitle("Задание " + i);
            task.setStatementMd("Условие");
            task.setCorrectAnswer("1");
            taskSetService.addTasks(set.getId(), List.of(taskService.create(task).getId()));
        }
        return set;
    }

    private User student() {
        return userRepository.save(new User(Role.STUDENT, "Ученик", "s-" + shortId(), "{noop}x"));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
