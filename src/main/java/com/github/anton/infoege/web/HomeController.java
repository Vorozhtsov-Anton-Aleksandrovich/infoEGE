package com.github.anton.infoege.web;

import com.github.anton.infoege.user.AppUserDetails;
import com.github.anton.infoege.user.Role;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserDetails user) {
        return user.getRole() == Role.TEACHER ? "redirect:/teacher" : "redirect:/student";
    }

    @GetMapping("/teacher")
    public String teacherHome() {
        return "teacher/home";
    }
}
