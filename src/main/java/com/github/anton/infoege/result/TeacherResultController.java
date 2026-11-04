package com.github.anton.infoege.result;

import com.github.anton.infoege.student.StudentService;
import com.github.anton.infoege.taskset.TaskSetService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class TeacherResultController {

    private final ResultService resultService;
    private final ResultCsvExporter csvExporter;
    private final TaskSetService taskSetService;
    private final StudentService studentService;

    public TeacherResultController(ResultService resultService, ResultCsvExporter csvExporter,
                                   TaskSetService taskSetService, StudentService studentService) {
        this.resultService = resultService;
        this.csvExporter = csvExporter;
        this.taskSetService = taskSetService;
        this.studentService = studentService;
    }

    @GetMapping("/teacher/results")
    public String list(@RequestParam(required = false) Long setId, @RequestParam(required = false) Long studentId,
                       Model model) {
        model.addAttribute("results", resultService.results(setId, studentId));
        model.addAttribute("sets", taskSetService.list());
        model.addAttribute("students", studentService.all());
        model.addAttribute("setId", setId);
        model.addAttribute("studentId", studentId);
        return "teacher/results/list";
    }

    @GetMapping("/teacher/results.csv")
    public ResponseEntity<String> summaryCsv(@RequestParam(required = false) Long setId,
                                             @RequestParam(required = false) Long studentId) {
        return csv("results", csvExporter.summary(resultService.results(setId, studentId)));
    }

    @GetMapping("/teacher/results/tasks.csv")
    public ResponseEntity<String> tasksCsv(@RequestParam(required = false) Long setId,
                                           @RequestParam(required = false) Long studentId) {
        return csv("results-tasks", csvExporter.tasks(resultService.results(setId, studentId)));
    }

    @GetMapping("/teacher/assignments/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ResultService.AssignmentResult result = resultService.result(id);
        if (result.attempt() == null) {
            return "redirect:/teacher/sets/" + result.assignment().getTaskSet().getId() + "#assignments";
        }
        model.addAttribute("r", result);
        model.addAttribute("tasks", resultService.taskResults(result));
        return "teacher/results/detail";
    }

    @PostMapping("/teacher/assignments/{id}/score")
    public String adjustScore(@PathVariable Long id, @RequestParam(required = false) Integer manualScore,
                              @RequestParam(required = false) String comment, RedirectAttributes redirect) {
        try {
            resultService.adjustScore(id, manualScore, comment);
            redirect.addFlashAttribute("message", manualScore == null
                    ? "Ручная корректировка снята, действует автоматический балл." : "Балл изменён.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/assignments/" + id;
    }

    private static ResponseEntity<String> csv(String name, String body) {
        String filename = name + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }
}
