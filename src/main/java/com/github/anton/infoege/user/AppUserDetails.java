package com.github.anton.infoege.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Пользователь в сессии. Хранит id, чтобы контроллеры проверяли доступ без лишнего запроса к БД.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final Role role;
    private final String displayName;
    private final String login;
    private final String passwordHash;
    private final boolean active;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.role = user.getRole();
        this.displayName = user.getDisplayName();
        this.login = user.getLogin();
        this.passwordHash = user.getPasswordHash();
        this.active = user.isActive();
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /** Сравнение по id: на нём основан поиск сессий пользователя в SessionRegistry. */
    @Override
    public boolean equals(Object o) {
        return o instanceof AppUserDetails other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
