package com.github.anton.infoege.student;

import com.github.anton.infoege.invite.InviteService;
import com.github.anton.infoege.result.ResultService;
import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RequestMapping("/teacher/students")
public class TeacherStudentController {

    private final StudentService studentService;
    private final InviteService inviteService;
    private final ResultService resultService;

    public TeacherStudentController(StudentService studentService, InviteService inviteService,
                                    ResultService resultService) {
        this.studentService = studentService;
        this.inviteService = inviteService;
        this.resultService = resultService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("students", studentService.all());
        model.addAttribute("stats", resultService.statsByStudent());
        model.addAttribute("invites", inviteService.pendingNewStudents());
        return "teacher/students/list";
    }

    @PostMapping("/invites")
    public String invite(@RequestParam String displayName, @AuthenticationPrincipal AppUserDetails teacher,
                         RedirectAttributes redirect) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty() || name.length() > 200) {
            redirect.addFlashAttribute("error", "Укажите имя ученика (до 200 символов).");
            return "redirect:/teacher/students";
        }
        showLink(redirect, name, inviteService.inviteNewStudent(name, teacher.getId()));
        return "redirect:/teacher/students";
    }

    @PostMapping("/invites/{inviteId}/reissue")
    public String reissueInvite(@PathVariable Long inviteId, @AuthenticationPrincipal AppUserDetails teacher,
                                RedirectAttributes redirect) {
        String token = inviteService.reissuePending(inviteId, teacher.getId());
        showLink(redirect, "новая ссылка", token);
        return "redirect:/teacher/students";
    }

    @PostMapping("/invites/{inviteId}/delete")
    public String deleteInvite(@PathVariable Long inviteId, RedirectAttributes redirect) {
        inviteService.deletePending(inviteId);
        redirect.addFlashAttribute("message", "Приглашение отозвано.");
        return "redirect:/teacher/students";
    }

    @GetMapping("/{id}")
    public String show(@PathVariable Long id, Model model) {
        model.addAttribute("student", studentService.get(id));
        model.addAttribute("summary", resultService.studentSummary(id));
        return "teacher/students/show";
    }

    @PostMapping("/{id}/reset-link")
    public String resetLink(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails teacher,
                            RedirectAttributes redirect) {
        User student = studentService.get(id);
        showLink(redirect, "сброс пароля: " + student.getDisplayName(), inviteService.issuePasswordReset(id, teacher.getId()));
        return "redirect:/teacher/students/" + id;
    }

    @PostMapping("/{id}/active")
    public String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        studentService.setActive(id, active);
        redirect.addFlashAttribute("message", active ? "Ученик снова может входить." : "Ученик деактивирован, его сессии завершены.");
        return "redirect:/teacher/students/" + id;
    }

    /** Ссылка показывается один раз: в БД хранится только хеш токена. */
    private static void showLink(RedirectAttributes redirect, String label, String token) {
        String link = ServletUriComponentsBuilder.fromCurrentContextPath().path("/invite/{token}").build(token).toString();
        redirect.addFlashAttribute("inviteLabel", label);
        redirect.addFlashAttribute("inviteLink", link);
    }
}
