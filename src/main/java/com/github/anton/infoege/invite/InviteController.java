package com.github.anton.infoege.invite;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Публичная страница регистрации и сброса пароля по одноразовой ссылке.
 */
@Controller
@RequestMapping("/invite/{token}")
public class InviteController {

    private static final Pattern LOGIN = Pattern.compile("[A-Za-z0-9._-]{3,32}");

    private final InviteService inviteService;

    public InviteController(InviteService inviteService) {
        this.inviteService = inviteService;
    }

    @GetMapping
    public String form(@PathVariable String token, Model model) {
        Optional<Invite> invite = inviteService.findUsable(token);
        if (invite.isEmpty()) {
            return "invite/invalid";
        }
        model.addAttribute("form", new RegistrationForm());
        addInviteAttributes(token, invite.get(), model);
        return "invite/register";
    }

    @PostMapping
    public String submit(@PathVariable String token, @Valid @ModelAttribute("form") RegistrationForm form,
                         BindingResult binding, Model model) {
        Optional<Invite> invite = inviteService.findUsable(token);
        if (invite.isEmpty()) {
            return "invite/invalid";
        }
        boolean reset = invite.get().isPasswordReset();
        if (!reset && (form.getLogin() == null || !LOGIN.matcher(form.getLogin().trim()).matches())) {
            binding.rejectValue("login", "login", "От 3 до 32 символов: латинские буквы, цифры, точка, дефис, подчёркивание");
        }
        if (form.getPassword() != null && !form.getPassword().equals(form.getPasswordConfirm())) {
            binding.rejectValue("passwordConfirm", "confirm", "Пароли не совпадают");
        }
        if (!binding.hasErrors()) {
            try {
                inviteService.redeem(token, form.getLogin(), form.getPassword());
                return reset ? "redirect:/login?passwordChanged" : "redirect:/login?registered";
            } catch (InviteService.InviteException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        addInviteAttributes(token, invite.get(), model);
        return "invite/register";
    }

    private void addInviteAttributes(String token, Invite invite, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("invite", invite);
        model.addAttribute("existingLogin", inviteService.loginOf(invite).orElse(null));
    }
}
