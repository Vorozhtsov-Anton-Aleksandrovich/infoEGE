package com.github.anton.infoege.attempt;

import com.github.anton.infoege.task.FileDownload;
import com.github.anton.infoege.task.Task;
import com.github.anton.infoege.task.TaskFile;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.taskset.TaskSet;
import com.github.anton.infoege.taskset.TaskSetService;
import com.github.anton.infoege.user.AppUserDetails;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Полноэкранная страница тренажёра. Правильные ответы и разборы сюда не попадают:
 * в модель кладутся только условие, файлы, размер таблицы ответа и сохранённый ответ ученика.
 */
@Controller
@RequestMapping("/trainer/attempts/{attemptId}")
public class TrainerController {

    private final AttemptService attemptService;
    private final TaskService taskService;
    private final TaskSetService taskSetService;

    public TrainerController(AttemptService attemptService, TaskService taskService, TaskSetService taskSetService) {
        this.attemptService = attemptService;
        this.taskService = taskService;
        this.taskSetService = taskSetService;
    }

    /** Задание на странице тренажёра. status: not-opened, opened или saved. */
    public record TrainerTask(int number, Long taskId, int egeNumber, String statementHtml, List<TaskFile> attachments,
                              int rows, int cols, List<List<String>> answer, String status) {
    }

    @GetMapping
    public String page(@PathVariable Long attemptId, @AuthenticationPrincipal AppUserDetails student, Model model,
                       RedirectAttributes redirect) {
        Attempt attempt = attemptService.open(attemptId, student.getId());
        Long assignmentId = attempt.getAssignment().getId();
        if (!attempt.isInProgress()) {
            redirect.addFlashAttribute("message", attempt.getStatus() == AttemptStatus.EXPIRED
                    ? "Время на работу вышло. Сохранённые ответы приняты." : "Работа завершена.");
            return "redirect:/student/assignments/" + assignmentId;
        }
        TaskSet set = taskSetService.get(attempt.getAssignment().getTaskSet().getId());
        model.addAttribute("attempt", attempt);
        model.addAttribute("title", set.getTitle());
        model.addAttribute("remainingSeconds", attempt.remainingSeconds(Instant.now()));
        model.addAttribute("tasks", trainerTasks(attemptId));
        return "trainer/trainer";
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long attemptId, @PathVariable Long fileId,
                                                    @AuthenticationPrincipal AppUserDetails student) throws IOException {
        TaskFile file = attemptService.file(attemptId, student.getId(), fileId);
        return FileDownload.of(file, taskService.openFile(file));
    }

    private List<TrainerTask> trainerTasks(Long attemptId) {
        List<AttemptAnswer> answers = attemptService.answers(attemptId);
        List<Long> taskIds = answers.stream().map(AttemptAnswer::getTaskId).toList();
        Map<Long, Task> tasks = taskService.tasksById(taskIds);
        Map<Long, List<TaskFile>> files = taskService.filesByTask(taskIds);
        List<TrainerTask> result = new ArrayList<>();
        for (AttemptAnswer answer : answers) {
            Task task = tasks.get(answer.getTaskId());
            List<TaskFile> taskFiles = files.getOrDefault(task.getId(), List.of());
            String html = taskService.renderMarkdown(task.getStatementMd(), taskFiles,
                    fileId -> "/trainer/attempts/" + attemptId + "/files/" + fileId);
            String status = answer.getAnswer() != null ? "saved" : answer.getFirstOpenedAt() != null ? "opened" : "not-opened";
            result.add(new TrainerTask(result.size() + 1, task.getId(), task.getEgeNumber(), html,
                    taskFiles.stream().filter(f -> !f.isImage()).toList(),
                    answer.rows(), answer.cols(), answer.getAnswer(), status));
        }
        return result;
    }
}
