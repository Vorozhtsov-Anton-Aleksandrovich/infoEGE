package com.github.anton.infoege.attempt;

import com.github.anton.infoege.assignment.Assignment;
import com.github.anton.infoege.assignment.AssignmentService;
import com.github.anton.infoege.course.CourseProgressService;
import com.github.anton.infoege.course.CourseRegistry;
import com.github.anton.infoege.student.StudentDashboardService;
import com.github.anton.infoege.taskset.TaskSetType;
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
    private final StudentDashboardService dashboardService;
    private final CourseRegistry courseRegistry;
    private final CourseProgressService courseProgressService;

    public StudentWorkController(AssignmentService assignmentService, AttemptService attemptService,
                                 StudentDashboardService dashboardService, CourseRegistry courseRegistry,
                                 CourseProgressService courseProgressService) {
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
        this.dashboardService = dashboardService;
        this.courseRegistry = courseRegistry;
        this.courseProgressService = courseProgressService;
    }

    /** Строка списка работ ученика. remainingSeconds — сколько времени осталось у начатой работы с лимитом. */
    public record WorkRow(Assignment assignment, Attempt attempt, WorkStatus status, boolean resultsVisible,
                          Long remainingSeconds) {
    }

    @GetMapping("/student")
    public String home(@AuthenticationPrincipal AppUserDetails student, Model model) {
        List<WorkRow> works = rows(student.getId());
        model.addAttribute("works", works);
        model.addAttribute("variants", works.stream()
                .filter(w -> w.assignment().getTaskSet().getType() == TaskSetType.VARIANT).toList());
        model.addAttribute("collections", works.stream()
                .filter(w -> w.assignment().getTaskSet().getType() != TaskSetType.VARIANT).toList());
        model.addAttribute("dashboard", dashboardService.dashboard(student.getId()));
        model.addAttribute("courses", courseRegistry.courses());
        model.addAttribute("courseProgress", courseProgressService.forUser(student.getId()));
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
                .map(a -> new WorkRow(a, attempts.get(a.getId()), WorkStatus.of(a, attempts.get(a.getId()), now),
                        ResultVisibility.visibleToStudent(a, attempts.get(a.getId()), now),
                        attempts.get(a.getId()) == null ? null : attempts.get(a.getId()).remainingSeconds(now)))
                .toList();
    }
}
