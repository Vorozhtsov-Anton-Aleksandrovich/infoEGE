package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.web.NotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Работы ученика: список, карточка работы, старт попытки.
 * Ученик видит только свои назначения: id берётся из сессии, не из запроса.
 */
@Controller
public class StudentWorkController {

    private final AssignmentService assignmentService;
    private final AttemptService attemptService;

    public StudentWorkController(AssignmentService assignmentService, AttemptService attemptService) {
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
    }

    /** Строка списка работ ученика. */
    public record WorkRow(Assignment assignment, Attempt attempt, WorkStatus status) {
    }

    @GetMapping("/student")
    public String home(@AuthenticationPrincipal AppUserDetails student, Model model) {
        model.addAttribute("works", rows(student.getId()));
        return "student/home";
    }

    @GetMapping("/student/assignments/{id}")
    public String assignment(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails student, Model model) {
        WorkRow row = rows(student.getId()).stream()
                .filter(r -> r.assignment().getId().equals(id))
                .findFirst()
                .orElseThrow(NotFoundException::new);
        model.addAttribute("work", row);
        return "student/assignment";
    }

    @PostMapping("/student/assignments/{id}/start")
    public String start(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails student,
                        RedirectAttributes redirect) {
        try {
            Attempt attempt = attemptService.start(id, student.getId());
            if (!attempt.isInProgress()) {
                return "redirect:/student/assignments/" + id;
            }
            return "redirect:/trainer/attempts/" + attempt.getId();
        } catch (AttemptService.DeadlinePassedException e) {
            redirect.addFlashAttribute("error", "Срок выполнения работы истёк.");
            return "redirect:/student/assignments/" + id;
        }
    }

    private List<WorkRow> rows(Long studentId) {
        List<Assignment> assignments = assignmentService.forStudent(studentId);
        Map<Long, Attempt> attempts = attemptService.forAssignments(assignments.stream().map(Assignment::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getAssignment().getId(), Function.identity()));
        Instant now = Instant.now();
        return assignments.stream()
                .map(a -> new WorkRow(a, attempts.get(a.getId()), WorkStatus.of(a, attempts.get(a.getId()), now)))
                .toList();
    }
}
