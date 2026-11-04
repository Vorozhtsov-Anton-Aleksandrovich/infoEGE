package com.github.anton.infoege.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLogin(String login);

    boolean existsByRole(Role role);

    boolean existsByLoginIgnoreCase(String login);

    List<User> findByRoleOrderByDisplayName(Role role);

    List<User> findByRoleAndActiveTrueOrderByDisplayName(Role role);
}
