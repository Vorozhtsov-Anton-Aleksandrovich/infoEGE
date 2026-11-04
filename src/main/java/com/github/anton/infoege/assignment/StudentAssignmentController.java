package com.github.anton.infoege.assignment;

import com.github.anton.infoege.user.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Список работ ученика. Ученик видит только свои назначения: id берётся из сессии, не из запроса.
 */
@Controller
public class StudentAssignmentController {

    private final AssignmentService assignmentService;

    public StudentAssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/student")
    public String home(@AuthenticationPrincipal AppUserDetails student, Model model) {
        model.addAttribute("assignments", assignmentService.forStudent(student.getId()));
        return "student/home";
    }
}
