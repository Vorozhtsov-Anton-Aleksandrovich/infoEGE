package com.github.anton.infoege.result;

import com.github.anton.infoege.task.TaskFile;
import com.github.anton.infoege.task.TaskService;
import com.github.anton.infoege.user.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Результаты ученика. Доступны только по своему назначению и только когда разрешено showResultsMode:
 * до этого момента правильные ответы и разборы ученику не отдаются (404).
 */
@Controller
public class StudentResultController {

    private final ResultService resultService;
    private final TaskService taskService;

    public StudentResultController(ResultService resultService, TaskService taskService) {
        this.resultService = resultService;
        this.taskService = taskService;
    }

    /** Задание в результатах ученика с условием и разбором в HTML. */
    public record StudentTaskResult(ResultService.TaskResult result, String statementHtml, String solutionHtml) {
    }

    @GetMapping("/student/assignments/{id}/result")
    public String result(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails student, Model model) {
        ResultService.AssignmentResult result = resultService.studentResult(id, student.getId());
        List<ResultService.TaskResult> tasks = resultService.taskResults(result);
        Map<Long, List<TaskFile>> files = taskService.filesByTask(tasks.stream().map(t -> t.task().getId()).toList());
        String fileUrlBase = "/trainer/attempts/" + result.attempt().getId() + "/files/";
        List<StudentTaskResult> rows = new ArrayList<>();
        for (ResultService.TaskResult task : tasks) {
            List<TaskFile> taskFiles = files.getOrDefault(task.task().getId(), List.of());
            rows.add(new StudentTaskResult(task,
                    taskService.renderMarkdown(task.task().getStatementMd(), taskFiles, fileId -> fileUrlBase + fileId),
                    taskService.renderMarkdown(task.task().getSolutionMd(), taskFiles, fileId -> fileUrlBase + fileId)));
        }
        model.addAttribute("r", result);
        model.addAttribute("tasks", rows);
        return "student/result";
    }
}
