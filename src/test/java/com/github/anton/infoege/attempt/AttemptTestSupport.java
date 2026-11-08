package com.github.anton.infoege.attempt;

import com.github.anton.infoege.IntegrationTest;
import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.assignment.ShowResultsMode;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskForm;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetForm;
import com.github.anton.infoege.taskset.TaskSetService;
import com.github.anton.infoege.taskset.TaskSetType;
import com.github.anton.infoege.user.Role;
import com.github.anton.infoege.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

/** Общие заготовки для тестов попыток: ученик, вариант с заданиями, назначение. */
abstract class AttemptTestSupport extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TaskService taskService;

    @Autowired
    TaskSetService taskSetService;

    @Autowired
    AssignmentService assignmentService;

    @Autowired
    AttemptService attemptService;

    @Autowired
    AttemptRepository attemptRepository;

    @Autowired
    AttemptAnswerRepository answerRepository;

    @Autowired
    JdbcTemplate jdbc;

    User student() {
        return userRepository.save(new User(Role.STUDENT, "Ученик", "st-" + shortId(), "{noop}x"));
    }

    Task task(int egeNumber, int rows, int cols, String correct) {
        TaskForm form = new TaskForm();
        form.setEgeNumber(egeNumber);
        form.setTitle("Задание " + egeNumber);
        form.setStatementMd("Условие задания " + egeNumber + " " + shortId());
        form.setAnswerRows(rows);
        form.setAnswerCols(cols);
        form.setCorrectAnswer(correct);
        form.setMaxPoints(2);
        form.setSolutionEnabled(true);
        form.setSolutionMd("Разбор-секрет " + egeNumber);
        return taskService.create(form);
    }

    TaskSet variant(Integer timeLimit, Task... tasks) {
        TaskSetForm form = new TaskSetForm();
        form.setType(timeLimit == null ? TaskSetType.COLLECTION : TaskSetType.VARIANT);
        form.setTitle("Вариант " + shortId());
        form.setTimeLimitMinutes(timeLimit);
        TaskSet set = taskSetService.create(form);
        taskSetService.addTasks(set.getId(), java.util.Arrays.stream(tasks).map(Task::getId).toList());
        return set;
    }

    Assignment assign(TaskSet set, User student) {
        return assign(set, student, ShowResultsMode.AFTER_FINISH);
    }

    Assignment assign(TaskSet set, User student, ShowResultsMode mode) {
        assignmentService.assign(set.getId(), List.of(student.getId()), null, mode);
        return assignmentService.forStudent(student.getId()).stream()
                .filter(a -> a.getTaskSet().getId().equals(set.getId())).findFirst().orElseThrow();
    }

    static String json(Long taskId, String cellsJson) {
        return "{\"taskId\":" + taskId + ",\"cells\":" + cellsJson + "}";
    }

    static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
