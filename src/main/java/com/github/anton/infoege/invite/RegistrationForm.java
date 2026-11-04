package com.github.anton.infoege.invite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Форма по одноразовой ссылке. Логин проверяется в контроллере: при сбросе пароля его нет.
 */
public class RegistrationForm {

    private String login;

    @NotBlank(message = "Введите пароль")
    @Size(min = 8, max = 72, message = "Пароль от 8 до 72 символов")
    private String password;

    private String passwordConfirm;

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}
